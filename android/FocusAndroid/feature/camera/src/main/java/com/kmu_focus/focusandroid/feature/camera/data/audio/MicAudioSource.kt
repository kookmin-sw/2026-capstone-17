package com.kmu_focus.focusandroid.feature.camera.data.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.util.Log
import com.kmu_focus.focusandroid.core.media.data.recorder.RealTimeRecorder
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * 마이크 PCM 입력을 AAC로 인코딩해 RealTimeRecorder에 sample 단위로 전달한다.
 */
class MicAudioSource @Inject constructor() : RealTimeRecorder.AudioTrackSource {

    private val released = AtomicBoolean(false)
    private val workerRunning = AtomicBoolean(false)
    private val bufferInfo = MediaCodec.BufferInfo()
    private val pcmReadBuffer: ByteBuffer = ByteBuffer.allocateDirect(DEFAULT_PCM_READ_BYTES)
    private val silentPcmBuffer = ByteArray(DEFAULT_PCM_READ_BYTES)
    private val encodedSamples = LinkedBlockingDeque<RealTimeRecorder.AudioSample>(MAX_ENCODED_SAMPLE_QUEUE_SIZE)

    private val audioRecord: AudioRecord
    private val audioEncoder: MediaCodec

    @Volatile
    private var workerThread: Thread? = null

    private var ptsOffsetUs: Long = 0L
    private var totalQueuedPcmFrames: Long = 0L
    private var totalReadBytes: Long = 0L
    private var positiveReadCount: Long = 0L
    private var nonPositiveReadCount: Long = 0L
    private var queuedSilentBytes: Long = 0L
    private var maxObservedAmplitude: Int = 0
    private var nonZeroSampleCount: Long = 0L
    private var totalSampleCount: Long = 0L

    @Volatile
    private var outputFormat: MediaFormat? = null
    @Volatile
    private var outputFormatFromEncoder: Boolean = false

    override val hasAudio: Boolean = true

    override val format: MediaFormat?
        get() = outputFormat

    init {
        var localAudioRecord: AudioRecord? = null
        var localAudioEncoder: MediaCodec? = null
        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_ENCODING,
            )
            require(minBufferSize > 0) { "AudioRecord 버퍼 크기 초기화 실패: $minBufferSize" }

            val recordBufferSize = (minBufferSize * 2).coerceAtLeast(DEFAULT_PCM_READ_BYTES)
            localAudioRecord = createAudioRecord(recordBufferSize)
            require(localAudioRecord.state == AudioRecord.STATE_INITIALIZED) {
                "AudioRecord 초기화 실패"
            }

            localAudioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME)
            localAudioEncoder.configure(
                buildEncoderInputFormat(),
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE,
            )
            localAudioEncoder.start()

            localAudioRecord.startRecording()
        } catch (error: Exception) {
            runCatching { localAudioRecord?.release() }
            runCatching { localAudioEncoder?.stop() }
            runCatching { localAudioEncoder?.release() }
            throw error
        }

        audioRecord = checkNotNull(localAudioRecord)
        audioEncoder = checkNotNull(localAudioEncoder)
        warmUpEncoderOutputFormat()
    }

    override fun seekTo(timeUs: Long) {
        if (released.get()) return
        ptsOffsetUs = timeUs.coerceAtLeast(0L)
        totalQueuedPcmFrames = 0L
        totalReadBytes = 0L
        positiveReadCount = 0L
        nonPositiveReadCount = 0L
        queuedSilentBytes = 0L
        maxObservedAmplitude = 0
        nonZeroSampleCount = 0L
        totalSampleCount = 0L
        clearEncodedSampleQueue()
        // 실시간 마이크 소스는 매 녹화마다 신규 인스턴스를 사용하므로 flush로 인코더 상태를 재리셋하지 않는다.
        ensureWorkerRunning()
    }

    override fun readNextSample(): RealTimeRecorder.AudioSample? {
        if (!hasAudio || released.get()) return null
        ensureWorkerRunning()
        return dequeueEncodedSample()
    }

    private fun ensureWorkerRunning() {
        if (released.get() || workerRunning.get()) return
        synchronized(this) {
            if (released.get() || workerRunning.get()) return
            workerRunning.set(true)
            workerThread = Thread(
                {
                    runEncodingLoop()
                },
                WORKER_THREAD_NAME,
            ).also { thread ->
                thread.isDaemon = true
                thread.start()
            }
        }
    }

    private fun runEncodingLoop() {
        try {
            while (workerRunning.get() && !released.get()) {
                queueMicInput(
                    readMode = AudioRecord.READ_BLOCKING,
                    allowSilentFallback = false,
                )
                drainEncodedSamplesToQueue()
            }
            drainEncodedSamplesToQueue(maxDrainCount = MAX_DRAIN_ON_SHUTDOWN)
        } finally {
            workerRunning.set(false)
        }
    }

    private fun drainEncodedSamplesToQueue(maxDrainCount: Int = MAX_DRAIN_PER_ITERATION) {
        var drained = 0
        while (drained < maxDrainCount) {
            val timeoutUs = if (drained == 0) DEQUEUE_TIMEOUT_US else 0L
            val sample = drainEncodedSample(timeoutUs) ?: break
            enqueueEncodedSample(sample)
            drained++
        }
    }

    private fun enqueueEncodedSample(sample: RealTimeRecorder.AudioSample) {
        if (!encodedSamples.offerLast(sample)) {
            encodedSamples.pollFirst()
            encodedSamples.offerLast(sample)
        }
    }

    private fun dequeueEncodedSample(): RealTimeRecorder.AudioSample? {
        return try {
            encodedSamples.poll(READ_SAMPLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
    }

    private fun clearEncodedSampleQueue() {
        encodedSamples.clear()
    }

    private fun queueMicInput(
        readMode: Int = AudioRecord.READ_NON_BLOCKING,
        allowSilentFallback: Boolean = false,
    ) {
        val inputIndex = audioEncoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
        if (inputIndex < 0) return

        val inputBuffer = audioEncoder.getInputBuffer(inputIndex)
        if (inputBuffer == null) {
            audioEncoder.queueInputBuffer(inputIndex, 0, 0, nextPresentationTimeUs(), 0)
            return
        }

        inputBuffer.clear()
        val readableSize = minOf(inputBuffer.remaining(), pcmReadBuffer.capacity())
        pcmReadBuffer.clear()
        pcmReadBuffer.limit(readableSize)
        val bytesRead = audioRecord.read(
            pcmReadBuffer,
            readableSize,
            readMode,
        )

        if (bytesRead <= 0) {
            nonPositiveReadCount++
            if (allowSilentFallback && readableSize > 0) {
                val silentSize = minOf(readableSize, silentPcmBuffer.size)
                val ptsUs = nextPresentationTimeUs()
                inputBuffer.put(silentPcmBuffer, 0, silentSize)
                audioEncoder.queueInputBuffer(inputIndex, 0, silentSize, ptsUs, 0)
                totalQueuedPcmFrames += (silentSize / BYTES_PER_PCM_FRAME)
                queuedSilentBytes += silentSize.toLong()
            } else {
                audioEncoder.queueInputBuffer(inputIndex, 0, 0, nextPresentationTimeUs(), 0)
            }
            return
        }

        // AudioRecord.read 후 write 모드 -> read 모드 전환 필수
        if (pcmReadBuffer.position() < bytesRead) {
            pcmReadBuffer.position(bytesRead.coerceAtMost(pcmReadBuffer.capacity()))
        }
        pcmReadBuffer.flip()
        if (pcmReadBuffer.remaining() <= 0 && bytesRead > 0) {
            pcmReadBuffer.position(0)
            pcmReadBuffer.limit(bytesRead.coerceAtMost(pcmReadBuffer.capacity()))
        }

        val readableBytes = minOf(
            bytesRead,
            pcmReadBuffer.remaining(),
            inputBuffer.remaining(),
        )
        if (readableBytes <= 0) {
            nonPositiveReadCount++
            audioEncoder.queueInputBuffer(inputIndex, 0, 0, nextPresentationTimeUs(), 0)
            return
        }

        applyPcmGainInPlace(pcmReadBuffer, readableBytes, PCM_GAIN)
        analyzePcmLevel(pcmReadBuffer, readableBytes)
        val originalLimit = pcmReadBuffer.limit()
        pcmReadBuffer.limit(pcmReadBuffer.position() + readableBytes)
        val ptsUs = nextPresentationTimeUs()
        inputBuffer.put(pcmReadBuffer)
        pcmReadBuffer.limit(originalLimit)
        audioEncoder.queueInputBuffer(
            inputIndex,
            0,
            readableBytes,
            ptsUs,
            0,
        )
        positiveReadCount++
        totalReadBytes += readableBytes.toLong()
        totalQueuedPcmFrames += (readableBytes / BYTES_PER_PCM_FRAME)
    }

    private fun warmUpEncoderOutputFormat() {
        repeat(FORMAT_WARMUP_RETRY_COUNT) {
            if (outputFormat != null) return
            queueMicInput(
                readMode = AudioRecord.READ_NON_BLOCKING,
                allowSilentFallback = true,
            )
            val outputIndex = audioEncoder.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
            when {
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    outputFormat = audioEncoder.outputFormat
                    outputFormatFromEncoder = true
                    return
                }

                outputIndex >= 0 -> {
                    audioEncoder.releaseOutputBuffer(outputIndex, false)
                }
            }
        }
        if (outputFormat == null) {
            outputFormat = buildMuxerAudioFormat()
            outputFormatFromEncoder = false
            Log.w(
                TAG,
                "warmUpEncoderOutputFormat fallback format 사용: 실제 encoder format 미확정",
            )
        }
    }

    private fun drainEncodedSample(timeoutUs: Long): RealTimeRecorder.AudioSample? {
        val outputIndex = audioEncoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
        return when {
            outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> null
            outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                outputFormat = audioEncoder.outputFormat
                outputFormatFromEncoder = true
                null
            }
            outputIndex >= 0 -> {
                val sample = createAudioSample(outputIndex, bufferInfo)
                audioEncoder.releaseOutputBuffer(outputIndex, false)
                sample
            }
            else -> null
        }
    }

    private fun createAudioSample(
        outputIndex: Int,
        info: MediaCodec.BufferInfo,
    ): RealTimeRecorder.AudioSample? {
        if (info.size <= 0) return null
        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) return null

        val outputBuffer = audioEncoder.getOutputBuffer(outputIndex) ?: return null
        outputBuffer.position(info.offset)
        outputBuffer.limit(info.offset + info.size)

        val copied = ByteBuffer.allocateDirect(info.size).apply {
            put(outputBuffer)
            flip()
        }
        val flags = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG.inv()
        val ptsUs = info.presentationTimeUs.coerceAtLeast(0L)
        val copiedInfo = MediaCodec.BufferInfo().apply {
            set(0, info.size, ptsUs, flags)
        }
        return RealTimeRecorder.AudioSample(
            buffer = copied,
            info = copiedInfo,
            presentationTimeUs = ptsUs,
        )
    }

    private fun nextPresentationTimeUs(): Long {
        val queuedDurationUs = (totalQueuedPcmFrames * MICROS_PER_SECOND) / SAMPLE_RATE
        return ptsOffsetUs + queuedDurationUs
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        workerRunning.set(false)
        workerThread?.interrupt()

        runCatching {
            if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop()
            }
        }
        workerThread?.let { thread ->
            if (thread !== Thread.currentThread() && thread.isAlive) {
                runCatching { thread.join(WORKER_JOIN_TIMEOUT_MS) }
            }
        }
        workerThread = null
        clearEncodedSampleQueue()
        Log.w(
            TAG,
            "release summary: formatFromEncoder=$outputFormatFromEncoder, readBytes=$totalReadBytes, positiveReads=$positiveReadCount, nonPositiveReads=$nonPositiveReadCount, queuedSilentBytes=$queuedSilentBytes, maxAmp=$maxObservedAmplitude, nonZeroSamples=$nonZeroSampleCount, totalSamples=$totalSampleCount",
        )
        runCatching { audioRecord.release() }

        runCatching { audioEncoder.stop() }
        runCatching { audioEncoder.release() }
    }

    private fun buildEncoderInputFormat(): MediaFormat =
        MediaFormat.createAudioFormat(AUDIO_MIME, SAMPLE_RATE, CHANNEL_COUNT).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AAC_BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_SIZE)
        }

    private fun createAudioRecord(recordBufferSize: Int): AudioRecord {
        val preferredSources = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
        )
        for (source in preferredSources) {
            val candidate = runCatching {
                @SuppressLint("MissingPermission")
                val record = AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AUDIO_ENCODING)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG)
                            .build(),
                    )
                    .setBufferSizeInBytes(recordBufferSize)
                    .build()
                record
            }.getOrNull()
            if (candidate != null && candidate.state == AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord source selected: $source")
                return candidate
            }
            runCatching { candidate?.release() }
        }

        @SuppressLint("MissingPermission")
        val fallbackRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AUDIO_ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build(),
            )
            .setBufferSizeInBytes(recordBufferSize)
            .build()
        return fallbackRecord
    }

    private fun analyzePcmLevel(buffer: ByteBuffer, bytesRead: Int) {
        if (bytesRead < BYTES_PER_PCM_FRAME) return
        val sampleBuffer = buffer.duplicate()
        val start = sampleBuffer.position()
        val endExclusive = (start + bytesRead).coerceAtMost(sampleBuffer.limit())
        if (endExclusive - start < BYTES_PER_PCM_FRAME) return
        sampleBuffer.limit(endExclusive)

        var localMax = maxObservedAmplitude
        var localNonZero = 0L
        var localSamples = 0L

        while (sampleBuffer.remaining() >= BYTES_PER_PCM_FRAME) {
            val low = sampleBuffer.get().toInt() and 0xFF
            val high = sampleBuffer.get().toInt()
            val sample = ((high shl 8) or low).toShort().toInt()
            val absSample = kotlin.math.abs(sample)
            if (absSample > 0) localNonZero++
            if (absSample > localMax) localMax = absSample
            localSamples++
        }

        maxObservedAmplitude = localMax
        nonZeroSampleCount += localNonZero
        totalSampleCount += localSamples
    }

    private fun applyPcmGainInPlace(buffer: ByteBuffer, bytesRead: Int, gain: Float) {
        if (gain == 1f || bytesRead < BYTES_PER_PCM_FRAME) return
        val sampleBuffer = buffer.duplicate()
        val start = sampleBuffer.position()
        val endExclusive = (start + bytesRead).coerceAtMost(sampleBuffer.limit())
        if (endExclusive - start < BYTES_PER_PCM_FRAME) return
        sampleBuffer.limit(endExclusive)

        while (sampleBuffer.remaining() >= BYTES_PER_PCM_FRAME) {
            val sampleOffset = sampleBuffer.position()
            val low = sampleBuffer.get(sampleOffset).toInt() and 0xFF
            val high = sampleBuffer.get(sampleOffset + 1).toInt()
            val sample = ((high shl 8) or low).toShort().toInt()
            val amplified = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            sampleBuffer.put(sampleOffset, (amplified and 0xFF).toByte())
            sampleBuffer.put(sampleOffset + 1, ((amplified shr 8) and 0xFF).toByte())
            sampleBuffer.position(sampleOffset + BYTES_PER_PCM_FRAME)
        }
    }

    private fun buildMuxerAudioFormat(): MediaFormat =
        MediaFormat.createAudioFormat(AUDIO_MIME, SAMPLE_RATE, CHANNEL_COUNT).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AAC_BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_SIZE)
            setByteBuffer("csd-0", ByteBuffer.wrap(buildAudioSpecificConfig()))
        }

    private fun buildAudioSpecificConfig(): ByteArray {
        val audioObjectType = 2 // AAC LC
        val frequencyIndex = AAC_FREQUENCY_INDEX_44K
        val channelConfig = CHANNEL_COUNT
        return byteArrayOf(
            ((audioObjectType shl 3) or (frequencyIndex shr 1)).toByte(),
            (((frequencyIndex and 0x01) shl 7) or (channelConfig shl 3)).toByte(),
        )
    }

    private companion object {
        private const val TAG = "MicAudioSource"
        private const val AUDIO_MIME = MediaFormat.MIMETYPE_AUDIO_AAC
        private const val SAMPLE_RATE = 44_100
        private const val CHANNEL_COUNT = 1
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val AAC_BIT_RATE = 128_000
        private const val MAX_INPUT_SIZE = 16 * 1024
        private const val DEFAULT_PCM_READ_BYTES = 4 * 1024
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val MICROS_PER_SECOND = 1_000_000L
        private const val AAC_FREQUENCY_INDEX_44K = 4
        private const val FORMAT_WARMUP_RETRY_COUNT = 60
        private const val BYTES_PER_SAMPLE_16BIT = 2
        private const val BYTES_PER_PCM_FRAME = CHANNEL_COUNT * BYTES_PER_SAMPLE_16BIT
        private const val MAX_DRAIN_PER_ITERATION = 8
        private const val MAX_DRAIN_ON_SHUTDOWN = 24
        private const val MAX_ENCODED_SAMPLE_QUEUE_SIZE = 32
        private const val READ_SAMPLE_TIMEOUT_MS = 20L
        private const val WORKER_JOIN_TIMEOUT_MS = 500L
        private const val WORKER_THREAD_NAME = "mic-audio-encoder"
        private const val PCM_GAIN = 1.0f
    }
}
