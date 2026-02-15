package com.kmu_focus.focusandroid.feature.video.data.gl

import com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.feature.video.domain.entity.ProcessedFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRendererRecordingPolicyTest {

    @Test
    fun `recording이 비활성이면 프레임이 있어도 제출하지 않는다`() {
        val frame = processedFrameWithNoFaces()

        val shouldSubmit = shouldSubmitFrameForRecording(
            recordingEnabled = false,
            processedFrame = frame
        )

        assertFalse(shouldSubmit)
    }

    @Test
    fun `recording이 활성이어도 processedFrame이 없으면 제출하지 않는다`() {
        val shouldSubmit = shouldSubmitFrameForRecording(
            recordingEnabled = true,
            processedFrame = null
        )

        assertFalse(shouldSubmit)
    }

    @Test
    fun `recording이 활성이고 얼굴이 없어도 프레임을 제출한다`() {
        val frame = processedFrameWithNoFaces()

        val shouldSubmit = shouldSubmitFrameForRecording(
            recordingEnabled = true,
            processedFrame = frame
        )

        assertTrue(shouldSubmit)
    }

    @Test
    fun `recording이 활성이고 얼굴이 있으면 프레임을 제출한다`() {
        val frame = ProcessedFrame(
            faces = listOf(
                DetectedFace(
                    x = 10,
                    y = 20,
                    width = 80,
                    height = 80,
                    confidence = 0.9f
                )
            ),
            frameWidth = 1280,
            frameHeight = 720,
            timestampMs = 1000L
        )

        val shouldSubmit = shouldSubmitFrameForRecording(
            recordingEnabled = true,
            processedFrame = frame
        )

        assertTrue(shouldSubmit)
    }

    @Test
    fun `인코더 버퍼 인덱스는 0에서 1로 토글된다`() {
        val resolved = nextEncoderBufferIndex(0)

        assertEquals(1, resolved)
    }

    @Test
    fun `인코더 버퍼 인덱스는 1에서 0으로 토글된다`() {
        val resolved = nextEncoderBufferIndex(1)

        assertEquals(0, resolved)
    }

    @Test
    fun `인코더 버퍼 인덱스는 연속 호출 시 0과 1을 반복한다`() {
        val first = nextEncoderBufferIndex(0)
        val second = nextEncoderBufferIndex(first)
        val third = nextEncoderBufferIndex(second)

        assertEquals(1, first)
        assertEquals(0, second)
        assertEquals(1, third)
    }

    private fun processedFrameWithNoFaces(): ProcessedFrame = ProcessedFrame(
        faces = emptyList(),
        frameWidth = 1280,
        frameHeight = 720,
        timestampMs = 1000L
    )
}
