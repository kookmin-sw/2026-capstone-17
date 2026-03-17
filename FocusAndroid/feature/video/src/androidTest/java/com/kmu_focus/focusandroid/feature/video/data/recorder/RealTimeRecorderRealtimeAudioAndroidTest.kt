package com.kmu_focus.focusandroid.core.media.data.recorder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class RealTimeRecorderRealtimeAudioAndroidTest {

    @Test
    fun `recording output already contains audio track and rebased timestamps`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outputFile = File(context.cacheDir, "realtime_audio_${System.nanoTime()}.mp4")
        if (outputFile.exists()) {
            outputFile.delete()
        }

        val fakeEncoder = FakeVideoEncoder(
            outputFormat = createAvcFormat(width = 16, height = 16),
            frames = listOf(
                EncodedFrame(
                    data = ByteBuffer.wrap(byteArrayOf(0, 0, 0, 1, 0x65, 0x11, 0x22, 0x33)),
                    ptsUs = 5_000_000L,
                    flags = MediaCodec.BUFFER_FLAG_KEY_FRAME,
                ),
                EncodedFrame(
                    data = ByteBuffer.wrap(byteArrayOf(0, 0, 0, 1, 0x41, 0x44, 0x55, 0x66)),
                    ptsUs = 5_033_000L,
                    flags = 0,
                ),
                EncodedFrame(
                    data = ByteBuffer.allocate(0),
                    ptsUs = 5_033_000L,
                    flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                ),
            ),
        )

        val audioSource = FakeAudioTrackSource(
            audioFormat = createAacFormat(),
            frames = listOf(
                AudioFrame(ptsUs = 0L, data = ByteArray(64) { 0x55.toByte() }),
                AudioFrame(ptsUs = 33_000L, data = ByteArray(64) { 0x33.toByte() }),
            ),
        )

        val recorder = RealTimeRecorder(
            encoderFactory = RealTimeRecorder.VideoEncoderFactory { _, _, _, _, _ -> fakeEncoder },
            enableBackgroundDrain = true,
        )

        try {
            recorder.start(
                width = 16,
                height = 16,
                outputFile = outputFile,
                audioTrackSource = audioSource,
                onInputSurfaceReady = {},
            )
            recorder.stop()

            val trackInfo = inspectTrackInfo(outputFile)
            assertTrue("녹화 파일에 비디오 트랙이 있어야 합니다", trackInfo.videoTrackIndex >= 0)
            assertTrue("녹화 파일에 오디오 트랙이 있어야 합니다", trackInfo.audioTrackIndex >= 0)
            assertTrue(
                "비디오 첫 PTS는 0 근처로 리베이스되어야 합니다: ${trackInfo.firstVideoPtsUs}",
                trackInfo.firstVideoPtsUs in 0L..100_000L,
            )
            assertTrue(
                "오디오 첫 PTS는 0 근처로 리베이스되어야 합니다: ${trackInfo.firstAudioPtsUs}",
                trackInfo.firstAudioPtsUs in 0L..100_000L,
            )
        } finally {
            outputFile.delete()
        }
    }

    private fun inspectTrackInfo(file: File): TrackInfo {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            for (trackIndex in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(trackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) videoTrackIndex = trackIndex
                if (mime.startsWith("audio/")) audioTrackIndex = trackIndex
            }

            TrackInfo(
                videoTrackIndex = videoTrackIndex,
                audioTrackIndex = audioTrackIndex,
                firstVideoPtsUs = if (videoTrackIndex >= 0) readFirstPtsUs(file, videoTrackIndex) else -1L,
                firstAudioPtsUs = if (audioTrackIndex >= 0) readFirstPtsUs(file, audioTrackIndex) else -1L,
            )
        } finally {
            extractor.release()
        }
    }

    private fun readFirstPtsUs(file: File, trackIndex: Int): Long {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            extractor.selectTrack(trackIndex)
            val buffer = ByteBuffer.allocateDirect(64 * 1024)
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize > 0) extractor.sampleTime else -1L
        } finally {
            extractor.release()
        }
    }

    private fun createAvcFormat(width: Int, height: Int): MediaFormat {
        return MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setByteBuffer(
                "csd-0",
                ByteBuffer.wrap(
                    byteArrayOf(
                        0x00, 0x00, 0x00, 0x01, 0x67, 0x42, 0x00, 0x1E,
                        0x96.toByte(), 0x54, 0x05, 0x01, 0xED.toByte(), 0x00,
                        0xF0.toByte(), 0x88.toByte(), 0x45, 0x80.toByte(),
                    ),
                ),
            )
            setByteBuffer(
                "csd-1",
                ByteBuffer.wrap(
                    byteArrayOf(
                        0x00, 0x00, 0x00, 0x01, 0x68, 0xCE.toByte(), 0x3C, 0x80.toByte(),
                    ),
                ),
            )
        }
    }

    private fun createAacFormat(): MediaFormat {
        return MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44_100, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setByteBuffer("csd-0", ByteBuffer.wrap(byteArrayOf(0x12, 0x10)))
        }
    }

    private data class TrackInfo(
        val videoTrackIndex: Int,
        val audioTrackIndex: Int,
        val firstVideoPtsUs: Long,
        val firstAudioPtsUs: Long,
    )

    private data class EncodedFrame(
        val data: ByteBuffer,
        val ptsUs: Long,
        val flags: Int,
    )

    private data class AudioFrame(
        val ptsUs: Long,
        val data: ByteArray,
        val flags: Int = 0,
    )

    private class FakeVideoEncoder(
        override val outputFormat: MediaFormat,
        private val frames: List<EncodedFrame>,
    ) : RealTimeRecorder.VideoEncoder {
        private val surfaceTexture = SurfaceTexture(0)
        private val surface = Surface(surfaceTexture)
        private var nextIndex = -1
        private var released = false

        override fun createInputSurface(): Surface = surface

        override fun dequeueOutputBuffer(info: MediaCodec.BufferInfo, timeoutUs: Long): Int {
            if (released) return MediaCodec.INFO_TRY_AGAIN_LATER
            if (nextIndex < 0) {
                nextIndex = 0
                return MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
            }
            if (nextIndex >= frames.size) return MediaCodec.INFO_TRY_AGAIN_LATER

            val frame = frames[nextIndex]
            info.offset = 0
            info.size = frame.data.remaining()
            info.presentationTimeUs = frame.ptsUs
            info.flags = frame.flags
            return nextIndex++
        }

        override fun getOutputBuffer(index: Int): ByteBuffer? {
            return frames.getOrNull(index)?.data?.duplicate()
        }

        override fun releaseOutputBuffer(index: Int, render: Boolean) = Unit

        override fun signalEndOfInputStream() = Unit

        override fun stopAndRelease() {
            if (released) return
            released = true
            surface.release()
            surfaceTexture.release()
        }
    }

    private class FakeAudioTrackSource(
        private val audioFormat: MediaFormat,
        private val frames: List<AudioFrame>,
    ) : RealTimeRecorder.AudioTrackSource {
        private var released = false
        private var currentIndex = 0

        override val hasAudio: Boolean = true

        override val format: MediaFormat = audioFormat

        override fun seekTo(timeUs: Long) {
            currentIndex = frames.indexOfFirst { it.ptsUs >= timeUs }.let { index ->
                if (index < 0) frames.size else index
            }
        }

        override fun readNextSample(): RealTimeRecorder.AudioSample? {
            if (released) return null
            val frame = frames.getOrNull(currentIndex) ?: return null
            currentIndex += 1

            val buffer = ByteBuffer.allocateDirect(frame.data.size).apply {
                put(frame.data)
                flip()
            }
            val info = MediaCodec.BufferInfo().apply {
                offset = 0
                size = frame.data.size
                presentationTimeUs = frame.ptsUs
                flags = frame.flags
            }
            return RealTimeRecorder.AudioSample(
                buffer = buffer,
                info = info,
                presentationTimeUs = frame.ptsUs,
            )
        }

        override fun release() {
            released = true
        }
    }
}
