package com.kmu_focus.focusandroid.feature.video.data.recorder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GLSurfaceView 렌더 스레드에서 그려지는 FBO를 그대로 H.264 MP4로 인코딩하는 실시간 녹화기.
 *
 * - 입력: Surface 기반 H.264 인코더 (COLOR_FormatSurface)
 * - 출력: MediaMuxer를 통해 비디오(+선택 오디오) MP4 파일
 * - 타임스탬프: GL 쪽에서 eglPresentationTimeANDROID로 전달 (ns)
 */
class RealTimeRecorder(
    private val encoderFactory: VideoEncoderFactory = DefaultVideoEncoderFactory(),
    private val muxerFactory: VideoMuxerFactory = DefaultVideoMuxerFactory(),
    private val loggerTag: String = "RealTimeRecorder",
    /** 테스트 등에서 백그라운드 drain 스레드를 끄고 싶을 때 false로 설정. */
    private val enableBackgroundDrain: Boolean = true,
) {

    @Volatile
    var isRecording: Boolean = false
        private set

    private val draining = AtomicBoolean(false)

    @Volatile
    private var encoder: VideoEncoder? = null

    @Volatile
    private var muxer: VideoMuxer? = null

    @Volatile
    private var drainThread: Thread? = null

    @Volatile
    private var audioTrackSource: AudioTrackSource? = null

    @Volatile
    private var muxerStarted = false

    @Volatile
    private var videoTrackIndex: Int = -1

    @Volatile
    private var audioTrackIndex: Int = -1

    @Volatile
    private var lastVideoPtsUs: Long = -1L

    @Volatile
    var lastRecordingSampleCount: Int = 0
        private set

    @Volatile
    private var currentRecordingSampleCount: Int = 0

    @Volatile
    private var currentAudioSampleCount: Int = 0

    private val bufferInfo = MediaCodec.BufferInfo()
    private var pendingAudioSample: AudioSample? = null
    private var videoPtsBaseUs: Long = Long.MIN_VALUE

    /**
     * 비디오 인코더 + Muxer를 초기화하고, 인코더 입력 Surface를 콜백으로 전달한다.
     *
     * @param width 인코딩 해상도 (픽셀)
     * @param height 인코딩 해상도 (픽셀)
     * @param bitRate 비트레이트 (bps)
     * @param frameRate 목표 프레임레이트 (fps)
     * @param outputFile 출력 MP4 파일
     * @param audioTrackSource 원본 오디오 sample 공급자 (nullable)
     * @param audioStartPositionUs 오디오 seek 시작 위치 (us)
     * @param onInputSurfaceReady GLSurfaceView에서 사용할 인코더 입력 Surface 콜백
     */
    @Synchronized
    fun start(
        width: Int,
        height: Int,
        outputFile: File,
        bitRate: Int = DEFAULT_BITRATE,
        frameRate: Int = DEFAULT_FRAME_RATE,
        audioTrackSource: AudioTrackSource? = null,
        audioStartPositionUs: Long = 0L,
        onInputSurfaceReady: (Surface) -> Unit,
    ) {
        check(!isRecording) { "이미 녹화 중입니다" }

        val encoder = encoderFactory.create(
            width = width,
            height = height,
            bitRate = bitRate,
            frameRate = frameRate,
        )
        val muxer = muxerFactory.create(outputFile)

        val surface = encoder.createInputSurface()

        val preparedAudioTrack = prepareAudioTrackSource(audioTrackSource, audioStartPositionUs)
        val audioTrackIndex = if (preparedAudioTrack != null) {
            try {
                muxer.addTrack(preparedAudioTrack.format ?: throw IllegalStateException("audio format is null"))
            } catch (e: Exception) {
                Log.w(loggerTag, "오디오 트랙 등록 실패. 비디오-only로 진행합니다.", e)
                preparedAudioTrack.release()
                -1
            }
        } else {
            -1
        }

        this.encoder = encoder
        this.muxer = muxer
        this.audioTrackSource = preparedAudioTrack.takeIf { audioTrackIndex >= 0 }
        this.muxerStarted = false
        this.videoTrackIndex = -1
        this.audioTrackIndex = audioTrackIndex
        this.lastVideoPtsUs = -1L
        this.pendingAudioSample = null
        this.videoPtsBaseUs = Long.MIN_VALUE
        this.currentRecordingSampleCount = 0
        this.currentAudioSampleCount = 0

        isRecording = true
        draining.set(true)

        if (enableBackgroundDrain) {
            // 인코더 출력 draining 스레드 시작
            val thread = Thread({ drainLoop() }, "realtime-encoder-drain")
            thread.isDaemon = true
            thread.start()
            drainThread = thread
        }

        onInputSurfaceReady(surface)
    }

    /**
     * 더 이상 프레임을 보내지 않음을 인코더에 알리고, draining 스레드가 정리되도록 한다.
     * 반복 호출해도 안전하다.
     *
     * 주의: join() 전에 락을 해제해야 한다. releaseInternal()이 @Synchronized이므로
     * 락을 잡은 채 join하면 drain 스레드가 releaseInternal() 진입 시 데드락 발생.
     */
    fun stop() {
        val (threadToJoin, backgroundEnabled) = synchronized(this) {
            if (!isRecording) return
            isRecording = false

            try {
                encoder?.signalEndOfInputStream()
            } catch (e: Exception) {
                Log.w(loggerTag, "signalEndOfInputStream 실패", e)
            }
            drainThread to enableBackgroundDrain
        }

        if (!backgroundEnabled) {
            releaseInternal()
            return
        }

        if (threadToJoin != null && threadToJoin.isAlive) {
            try {
                threadToJoin.join(DRAIN_JOIN_TIMEOUT_MS)
            } catch (e: InterruptedException) {
                Log.w(loggerTag, "drainThread join 인터럽트", e)
            }
        }
    }

    private fun drainLoop() {
        val encoder = this.encoder
        val muxer = this.muxer

        if (encoder == null || muxer == null) {
            Log.w(loggerTag, "drainLoop: encoder 또는 muxer가 null입니다")
            releaseInternal()
            return
        }

        var tryAgainCount = 0
        var sawOutputEos = false
        try {
            while (draining.get() && !sawOutputEos) {
                val bufferIndex = encoder.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                when {
                    bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        tryAgainCount++
                        if (!isRecording) {
                            drainAudioSamplesUpTo(lastVideoPtsUs)
                            if (tryAgainCount >= MAX_TRY_AGAIN_AFTER_STOP) {
                                Log.w(loggerTag, "EOS 대기 중 타임아웃. 정리로 진행합니다.")
                                break
                            }
                        } else if (!muxerStarted && (tryAgainCount <= 3 || tryAgainCount % 120 == 0)) {
                            Log.w(
                                loggerTag,
                                "drainLoop INFO_TRY_AGAIN_LATER (muxerStarted=$muxerStarted, sampleCount=$currentRecordingSampleCount, tries=$tryAgainCount)",
                            )
                        }
                    }

                    bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // 비디오 트랙 등록 후, 오디오 트랙 상태까지 확인해 Muxer 시작
                        val newFormat = encoder.outputFormat
                        videoTrackIndex = muxer.addTrack(newFormat)
                        startMuxerIfReady(muxer)
                        Log.w(
                            loggerTag,
                            "drainLoop INFO_OUTPUT_FORMAT_CHANGED: videoTrackIndex=$videoTrackIndex, audioTrackIndex=$audioTrackIndex, format=$newFormat",
                        )
                    }

                    bufferIndex >= 0 -> {
                        tryAgainCount = 0
                        val encodedData = encoder.getOutputBuffer(bufferIndex)
                        if (encodedData == null) {
                            encoder.releaseOutputBuffer(bufferIndex, false)
                            continue
                        }

                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }

                        if (bufferInfo.size > 0 && muxerStarted && videoTrackIndex >= 0) {
                            if (videoPtsBaseUs == Long.MIN_VALUE) {
                                videoPtsBaseUs = bufferInfo.presentationTimeUs
                            }
                            val rebasedVideoPtsUs = (bufferInfo.presentationTimeUs - videoPtsBaseUs).coerceAtLeast(0L)
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            bufferInfo.presentationTimeUs = rebasedVideoPtsUs
                            muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                            currentRecordingSampleCount++
                            lastVideoPtsUs = rebasedVideoPtsUs
                            drainAudioSamplesUpTo(rebasedVideoPtsUs)
                            if (currentRecordingSampleCount == 1) {
                                Log.w(
                                    loggerTag,
                                    "drainLoop first video sample: size=${bufferInfo.size}, ptsUs=$rebasedVideoPtsUs",
                                )
                            }
                        } else if (bufferInfo.size > 0 && !muxerStarted) {
                            startMuxerIfReady(muxer)
                            Log.w(
                                loggerTag,
                                "drainLoop sample before muxer start: size=${bufferInfo.size}, flags=${bufferInfo.flags}",
                            )
                        }

                        val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        encoder.releaseOutputBuffer(bufferIndex, false)

                        if (isEos && !isRecording) {
                            sawOutputEos = true
                        }
                    }
                }
            }
            drainAudioSamplesUpTo(lastVideoPtsUs)
        } catch (e: Exception) {
            Log.e(loggerTag, "drainLoop 실패", e)
        } finally {
            releaseInternal()
        }
    }

    private fun prepareAudioTrackSource(
        source: AudioTrackSource?,
        startPositionUs: Long,
    ): AudioTrackSource? {
        if (source == null) return null
        if (!source.hasAudio) {
            source.release()
            return null
        }

        val audioFormat = source.format
        if (audioFormat == null) {
            source.release()
            return null
        }

        return try {
            source.seekTo(startPositionUs.coerceAtLeast(0L))
            source
        } catch (e: Exception) {
            Log.w(loggerTag, "오디오 seek 실패. 비디오-only로 진행합니다.", e)
            source.release()
            null
        }
    }

    private fun startMuxerIfReady(muxer: VideoMuxer) {
        if (muxerStarted) return
        if (videoTrackIndex < 0) return
        if (audioTrackSource != null && audioTrackIndex < 0) return
        muxer.start()
        muxerStarted = true
    }

    private fun drainAudioSamplesUpTo(targetVideoPtsUs: Long) {
        if (!muxerStarted || audioTrackIndex < 0) return
        val source = audioTrackSource ?: return
        val muxer = muxer ?: return
        if (targetVideoPtsUs < 0) return

        if (pendingAudioSample == null) {
            pendingAudioSample = source.readNextSample()
        }

        while (true) {
            val sample = pendingAudioSample ?: break
            if (sample.presentationTimeUs > targetVideoPtsUs) {
                break
            }
            muxer.writeSampleData(audioTrackIndex, sample.buffer, sample.info)
            currentAudioSampleCount++
            pendingAudioSample = source.readNextSample()
        }
    }

    @Synchronized
    private fun releaseInternal() {
        draining.set(false)
        isRecording = false

        try {
            audioTrackSource?.release()
        } catch (e: Exception) {
            Log.w(loggerTag, "audioTrackSource release 실패", e)
        } finally {
            audioTrackSource = null
            pendingAudioSample = null
        }

        try {
            encoder?.stopAndRelease()
        } catch (e: Exception) {
            Log.w(loggerTag, "encoder stop/release 실패", e)
        } finally {
            encoder = null
        }

        try {
            if (muxerStarted) {
                muxer?.stopAndRelease()
            } else {
                muxer?.releaseQuietly()
            }
        } catch (e: Exception) {
            Log.w(loggerTag, "muxer stop/release 실패", e)
        } finally {
            muxer = null
        }

        drainThread = null
        lastRecordingSampleCount = currentRecordingSampleCount
        currentRecordingSampleCount = 0
        currentAudioSampleCount = 0
        muxerStarted = false
        videoTrackIndex = -1
        audioTrackIndex = -1
        lastVideoPtsUs = -1L
        videoPtsBaseUs = Long.MIN_VALUE
    }

    data class AudioSample(
        val buffer: ByteBuffer,
        val info: MediaCodec.BufferInfo,
        val presentationTimeUs: Long,
    )

    interface AudioTrackSource {
        val hasAudio: Boolean
        val format: MediaFormat?
        fun seekTo(timeUs: Long)
        fun readNextSample(): AudioSample?
        fun release()
    }

    interface VideoEncoder {
        fun createInputSurface(): Surface
        fun dequeueOutputBuffer(info: MediaCodec.BufferInfo, timeoutUs: Long): Int
        fun getOutputBuffer(index: Int): ByteBuffer?
        val outputFormat: MediaFormat
        fun releaseOutputBuffer(index: Int, render: Boolean)
        fun signalEndOfInputStream()
        fun stopAndRelease()
    }

    fun interface VideoEncoderFactory {
        fun create(
            width: Int,
            height: Int,
            bitRate: Int,
            frameRate: Int,
        ): VideoEncoder
    }

    interface VideoMuxer {
        fun addTrack(format: MediaFormat): Int
        fun start()
        fun writeSampleData(trackIndex: Int, byteBuf: ByteBuffer, info: MediaCodec.BufferInfo)
        fun stopAndRelease()
        fun releaseQuietly()
    }

    fun interface VideoMuxerFactory {
        fun create(outputFile: File): VideoMuxer
    }

    private class DefaultVideoEncoderFactory : VideoEncoderFactory {
        override fun create(
            width: Int,
            height: Int,
            bitRate: Int,
            frameRate: Int,
        ): VideoEncoder {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, DEFAULT_I_FRAME_INTERVAL_SEC)
            }

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = codec.createInputSurface()
            codec.start()

            return object : VideoEncoder {
                override fun createInputSurface(): Surface = inputSurface

                override fun dequeueOutputBuffer(info: MediaCodec.BufferInfo, timeoutUs: Long): Int {
                    return codec.dequeueOutputBuffer(info, timeoutUs)
                }

                override fun getOutputBuffer(index: Int): ByteBuffer? {
                    return codec.getOutputBuffer(index)
                }

                override val outputFormat: MediaFormat
                    get() = codec.outputFormat

                override fun releaseOutputBuffer(index: Int, render: Boolean) {
                    codec.releaseOutputBuffer(index, render)
                }

                override fun signalEndOfInputStream() {
                    codec.signalEndOfInputStream()
                }

                override fun stopAndRelease() {
                    try {
                        codec.stop()
                    } finally {
                        codec.release()
                    }
                }
            }
        }
    }

    private class DefaultVideoMuxerFactory : VideoMuxerFactory {
        override fun create(outputFile: File): VideoMuxer {
            val muxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            )
            return object : VideoMuxer {
                override fun addTrack(format: MediaFormat): Int = muxer.addTrack(format)

                override fun start() {
                    muxer.start()
                }

                override fun writeSampleData(
                    trackIndex: Int,
                    byteBuf: ByteBuffer,
                    info: MediaCodec.BufferInfo,
                ) {
                    muxer.writeSampleData(trackIndex, byteBuf, info)
                }

                override fun stopAndRelease() {
                    try {
                        muxer.stop()
                    } finally {
                        muxer.release()
                    }
                }

                override fun releaseQuietly() {
                    try {
                        muxer.release()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_BITRATE = 10_000_000
        private const val DEFAULT_FRAME_RATE = 30
        private const val DEFAULT_I_FRAME_INTERVAL_SEC = 1
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val DRAIN_JOIN_TIMEOUT_MS = 2_000L
        private const val MAX_TRY_AGAIN_AFTER_STOP = 240
    }
}
