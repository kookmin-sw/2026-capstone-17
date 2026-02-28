package com.kmu_focus.focusandroid.core.media.data.recorder

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.annotation.VisibleForTesting
import java.nio.ByteBuffer

/**
 * Source 비디오의 오디오 트랙을 추출해 RealTimeRecorder에 sample 단위로 제공한다.
 *
 * - seekTo()로 시작 위치를 맞춘다.
 * - readNextSample()의 presentationTimeUs는 seek 기준 0으로 리베이스된다.
 */
class AudioTrackExtractor internal constructor(
    private val extractorBackend: ExtractorBackend,
    sampleBufferSize: Int = DEFAULT_SAMPLE_BUFFER_SIZE,
    private val seekMode: Int = MediaExtractor.SEEK_TO_CLOSEST_SYNC,
) : RealTimeRecorder.AudioTrackSource {

    @Volatile
    private var released = false

    private val sampleBuffer: ByteBuffer = ByteBuffer.allocateDirect(sampleBufferSize.coerceAtLeast(1))
    private val audioTrackIndex: Int
    private var baseTimeUs: Long = 0L

    override val hasAudio: Boolean
    override val format: MediaFormat?

    init {
        var foundIndex = -1
        var foundFormat: MediaFormat? = null

        val trackCount = runCatching { extractorBackend.trackCount }.getOrDefault(0)
        for (trackIndex in 0 until trackCount) {
            val trackFormat = runCatching { extractorBackend.getTrackFormat(trackIndex) }.getOrNull() ?: continue
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(AUDIO_MIME_PREFIX)) {
                foundIndex = trackIndex
                foundFormat = trackFormat
                break
            }
        }

        audioTrackIndex = foundIndex
        format = foundFormat
        hasAudio = audioTrackIndex >= 0 && format != null

        if (hasAudio) {
            runCatching { extractorBackend.selectTrack(audioTrackIndex) }
        }
    }

    override fun seekTo(timeUs: Long) {
        if (!hasAudio || released) return
        baseTimeUs = timeUs.coerceAtLeast(0L)
        runCatching { extractorBackend.seekTo(baseTimeUs, seekMode) }
    }

    override fun readNextSample(): RealTimeRecorder.AudioSample? {
        if (!hasAudio || released) return null

        sampleBuffer.clear()
        val sampleSize = runCatching { extractorBackend.readSampleData(sampleBuffer, 0) }.getOrDefault(-1)
        if (sampleSize <= 0) return null

        val sampleTimeUs = runCatching { extractorBackend.sampleTime }.getOrDefault(-1L)
        if (sampleTimeUs < 0L) return null

        val rebasedPtsUs = (sampleTimeUs - baseTimeUs).coerceAtLeast(0L)
        val bufferInfo = MediaCodec.BufferInfo().apply {
            offset = 0
            size = sampleSize
            presentationTimeUs = rebasedPtsUs
            flags = runCatching { extractorBackend.sampleFlags }.getOrDefault(0)
        }

        val sample = sampleBuffer.duplicate().apply {
            position(0)
            limit(sampleSize)
        }

        runCatching { extractorBackend.advance() }
        return RealTimeRecorder.AudioSample(
            buffer = sample,
            info = bufferInfo,
            presentationTimeUs = rebasedPtsUs,
        )
    }

    override fun release() {
        if (released) return
        released = true
        runCatching { extractorBackend.release() }
    }

    fun interface Factory {
        fun create(sourceUri: String): RealTimeRecorder.AudioTrackSource
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    interface ExtractorBackend {
        val trackCount: Int
        fun getTrackFormat(index: Int): MediaFormat
        fun selectTrack(index: Int)
        fun seekTo(timeUs: Long, mode: Int)
        fun readSampleData(byteBuffer: ByteBuffer, offset: Int): Int
        val sampleTime: Long
        val sampleFlags: Int
        fun advance(): Boolean
        fun release()
    }

    companion object {
        fun create(sourceUri: String, context: Context): AudioTrackExtractor {
            val backend = AndroidExtractorBackend.create(sourceUri, context)
                ?: return AudioTrackExtractor(EmptyExtractorBackend, sampleBufferSize = 1)
            return AudioTrackExtractor(backend)
        }

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        fun createForTest(
            backend: ExtractorBackend,
            sampleBufferSize: Int = DEFAULT_SAMPLE_BUFFER_SIZE,
        ): AudioTrackExtractor = AudioTrackExtractor(
            extractorBackend = backend,
            sampleBufferSize = sampleBufferSize,
        )

        private const val AUDIO_MIME_PREFIX = "audio/"
        private const val DEFAULT_SAMPLE_BUFFER_SIZE = 512 * 1024
    }
}

private class AndroidExtractorBackend private constructor(
    private val mediaExtractor: MediaExtractor,
) : AudioTrackExtractor.ExtractorBackend {

    override val trackCount: Int
        get() = mediaExtractor.trackCount

    override fun getTrackFormat(index: Int): MediaFormat = mediaExtractor.getTrackFormat(index)

    override fun selectTrack(index: Int) {
        mediaExtractor.selectTrack(index)
    }

    override fun seekTo(timeUs: Long, mode: Int) {
        mediaExtractor.seekTo(timeUs, mode)
    }

    override fun readSampleData(byteBuffer: ByteBuffer, offset: Int): Int {
        return mediaExtractor.readSampleData(byteBuffer, offset)
    }

    override val sampleTime: Long
        get() = mediaExtractor.sampleTime

    override val sampleFlags: Int
        get() = mediaExtractor.sampleFlags

    override fun advance(): Boolean = mediaExtractor.advance()

    override fun release() {
        mediaExtractor.release()
    }

    companion object {
        fun create(sourceUri: String, context: Context): AndroidExtractorBackend? {
            val extractor = runCatching { MediaExtractor() }.getOrNull() ?: return null
            return try {
                extractor.setDataSource(context, Uri.parse(sourceUri), null)
                AndroidExtractorBackend(extractor)
            } catch (_: Throwable) {
                runCatching { extractor.release() }
                null
            }
        }
    }
}

private object EmptyExtractorBackend : AudioTrackExtractor.ExtractorBackend {
    override val trackCount: Int = 0

    override fun getTrackFormat(index: Int): MediaFormat {
        throw IndexOutOfBoundsException("empty extractor")
    }

    override fun selectTrack(index: Int) {}

    override fun seekTo(timeUs: Long, mode: Int) {}

    override fun readSampleData(byteBuffer: ByteBuffer, offset: Int): Int = -1

    override val sampleTime: Long = -1L

    override val sampleFlags: Int = 0

    override fun advance(): Boolean = false

    override fun release() {}
}
