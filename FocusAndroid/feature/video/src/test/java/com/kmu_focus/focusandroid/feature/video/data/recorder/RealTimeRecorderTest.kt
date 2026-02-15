package com.kmu_focus.focusandroid.feature.video.data.recorder

import android.view.Surface
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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

        // 두 번째 호출은 예외가 나와야 한다.
        recorder.start(
            width = 1280,
            height = 720,
            outputFile = output,
            onInputSurfaceReady = {},
        )
    }
}

