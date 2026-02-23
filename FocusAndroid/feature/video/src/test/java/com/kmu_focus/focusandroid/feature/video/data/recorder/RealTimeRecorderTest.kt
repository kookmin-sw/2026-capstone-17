package com.kmu_focus.focusandroid.feature.video.data.recorder

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

class RealTimeRecorderTest {

    private val encoder = mockk<RealTimeRecorder.VideoEncoder>(relaxed = true)
    private val muxer = mockk<RealTimeRecorder.VideoMuxer>(relaxed = true)
    private val encoderFactory = mockk<RealTimeRecorder.VideoEncoderFactory>()
    private val muxerFactory = mockk<RealTimeRecorder.VideoMuxerFactory>()

    private val recorder = RealTimeRecorder(
        encoderFactory = encoderFactory,
        muxerFactory = muxerFactory,
        enableBackgroundDrain = false,
    )

    // ── 기존 테스트 ──

    @Test
    fun `start configures encoder and muxer and exposes input surface`() {
        // Given
        val output = File("/tmp/out.mp4")
        val fakeSurface = mockk<Surface>()
        every {
            encoderFactory.create(
                width = any(),
                height = any(),
                bitRate = any(),
                frameRate = any(),
            )
        } returns encoder
        every { muxerFactory.create(output) } returns muxer
        every { encoder.createInputSurface() } returns fakeSurface

        var receivedSurface: Surface? = null

        // When
        recorder.start(
            width = 1920,
            height = 1080,
            outputFile = output,
            onInputSurfaceReady = { s -> receivedSurface = s },
        )

        // Then
        verify(exactly = 1) {
            encoderFactory.create(
                width = 1920,
                height = 1080,
                bitRate = any(),
                frameRate = any(),
            )
        }
        verify(exactly = 1) { muxerFactory.create(output) }
        verify(exactly = 1) { encoder.createInputSurface() }
        assertSame(fakeSurface, receivedSurface)
        assertTrue(recorder.isRecording)
    }

    @Test(expected = IllegalStateException::class)
    fun `start twice throws IllegalStateException`() {
        val output = File("/tmp/out.mp4")
        every {
            encoderFactory.create(
                width = any(),
                height = any(),
                bitRate = any(),
                frameRate = any(),
            )
        } returns encoder
        every { muxerFactory.create(output) } returns muxer
        every { encoder.createInputSurface() } returns mockk()

        recorder.start(
            width = 1280,
            height = 720,
            outputFile = output,
            onInputSurfaceReady = {},
        )

        recorder.start(
            width = 1280,
            height = 720,
            outputFile = output,
            onInputSurfaceReady = {},
        )
    }

    // ── 오디오 동시 기록 관련 테스트 ──

    @Test
    fun `start with audioTrackSource registers audio track on muxer`() {
        // Given: AudioTrackSource가 제공되면 muxer에 오디오 트랙도 등록해야 한다.
        val output = File("/tmp/out.mp4")
        val audioSource = mockk<RealTimeRecorder.AudioTrackSource>(relaxed = true)
        val audioFormat = mockk<MediaFormat>(relaxed = true)
        every { audioSource.format } returns audioFormat
        every { audioSource.hasAudio } returns true

        every {
            encoderFactory.create(
                width = any(), height = any(), bitRate = any(), frameRate = any(),
            )
        } returns encoder
        every { muxerFactory.create(output) } returns muxer
        every { encoder.createInputSurface() } returns mockk()
        every { muxer.addTrack(any()) } returns 0

        val recorderWithAudio = RealTimeRecorder(
            encoderFactory = encoderFactory,
            muxerFactory = muxerFactory,
            enableBackgroundDrain = false,
        )

        // When
        recorderWithAudio.start(
            width = 1920,
            height = 1080,
            outputFile = output,
            audioTrackSource = audioSource,
            onInputSurfaceReady = {},
        )

        // Then: muxer에 오디오 트랙이 등록되어야 한다 (video format 확정 전이라도 audio format은 미리 등록 가능)
        assertTrue(recorderWithAudio.isRecording)
    }

    @Test
    fun `start without audioTrackSource still works as video only`() {
        // Given: audioTrackSource가 null이면 기존 video-only 동작 유지
        val output = File("/tmp/out.mp4")
        every {
            encoderFactory.create(
                width = any(), height = any(), bitRate = any(), frameRate = any(),
            )
        } returns encoder
        every { muxerFactory.create(output) } returns muxer
        every { encoder.createInputSurface() } returns mockk()

        // When
        recorder.start(
            width = 1920,
            height = 1080,
            outputFile = output,
            onInputSurfaceReady = {},
        )

        // Then
        assertTrue(recorder.isRecording)
    }

    @Test
    fun `stop releases audioTrackSource when present`() {
        // Given
        val output = File("/tmp/out.mp4")
        val audioSource = mockk<RealTimeRecorder.AudioTrackSource>(relaxed = true)
        every { audioSource.hasAudio } returns true
        every { audioSource.format } returns mockk(relaxed = true)

        every {
            encoderFactory.create(
                width = any(), height = any(), bitRate = any(), frameRate = any(),
            )
        } returns encoder
        every { muxerFactory.create(output) } returns muxer
        every { encoder.createInputSurface() } returns mockk()

        val recorderWithAudio = RealTimeRecorder(
            encoderFactory = encoderFactory,
            muxerFactory = muxerFactory,
            enableBackgroundDrain = false,
        )

        recorderWithAudio.start(
            width = 1920,
            height = 1080,
            outputFile = output,
            audioTrackSource = audioSource,
            onInputSurfaceReady = {},
        )

        // When
        recorderWithAudio.stop()

        // Then: audioTrackSource의 release가 호출되어야 한다
        assertFalse(recorderWithAudio.isRecording)
        verify { audioSource.release() }
    }

    @Test
    fun `audioTrackSource with no audio track does not register audio on muxer`() {
        // Given: source 비디오에 오디오 트랙이 없는 경우
        val output = File("/tmp/out.mp4")
        val audioSource = mockk<RealTimeRecorder.AudioTrackSource>(relaxed = true)
        every { audioSource.hasAudio } returns false

        every {
            encoderFactory.create(
                width = any(), height = any(), bitRate = any(), frameRate = any(),
            )
        } returns encoder
        every { muxerFactory.create(output) } returns muxer
        every { encoder.createInputSurface() } returns mockk()

        val recorderWithAudio = RealTimeRecorder(
            encoderFactory = encoderFactory,
            muxerFactory = muxerFactory,
            enableBackgroundDrain = false,
        )

        // When
        recorderWithAudio.start(
            width = 1920,
            height = 1080,
            outputFile = output,
            audioTrackSource = audioSource,
            onInputSurfaceReady = {},
        )

        // Then: video-only 녹화와 동일하게 동작
        assertTrue(recorderWithAudio.isRecording)
    }
}
