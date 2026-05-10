package com.kmu_focus.focusandroid.core.media.data.gl

import com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
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

    @Test
    fun `프레임 타임스탬프가 뒤로 가면 분석 파이프라인 reset으로 판단한다`() {
        val shouldReset = hasAnalysisTimestampReset(
            lastFrameTimestampNs = 5_000_000_000L,
            frameTimestampNs = 1_000_000L,
        )

        assertTrue(shouldReset)
    }

    @Test
    fun `프레임 타임스탬프가 증가하면 분석 파이프라인 reset이 아니다`() {
        val shouldReset = hasAnalysisTimestampReset(
            lastFrameTimestampNs = 1_000_000L,
            frameTimestampNs = 5_000_000_000L,
        )

        assertFalse(shouldReset)
    }

    @Test
    fun `privacy blur ROI는 얼굴 원을 감싸는 union 영역으로 계산된다`() {
        val region = calculatePrivacyBlurRegion(
            ellipses = listOf(
                EllipseParams(
                    centerX = 0.50f,
                    centerY = 0.50f,
                    radiusX = 0.20f,
                    radiusY = 0.10f,
                    angle = 0f,
                )
            ),
            viewWidth = 200,
            viewHeight = 100,
        )

        assertEquals(0.23f, region?.regionRect?.minX ?: 0f, 0.0001f)
        assertEquals(0.32f, region?.regionRect?.minY ?: 0f, 0.0001f)
        assertEquals(0.77f, region?.regionRect?.maxX ?: 0f, 0.0001f)
        assertEquals(0.68f, region?.regionRect?.maxY ?: 0f, 0.0001f)
        assertEquals(20, region?.blurWidth)
        assertEquals(8, region?.blurHeight)
    }

    @Test
    fun `privacy blur 저해상도 크기는 얼굴이 클수록 더 작아진다`() {
        val small = resolvePrivacyBlurTextureSize(regionWidth = 120, regionHeight = 80)
        val large = resolvePrivacyBlurTextureSize(regionWidth = 320, regionHeight = 240)

        assertTrue(small.first > large.first)
        assertTrue(small.second > large.second)
    }

    @Test
    fun `녹화 중이 아니면 프리뷰는 현재 프레임을 그대로 사용한다`() {
        val selection = resolvePreviewFrameSelection(
            recordingEnabled = false,
            processedFrame = processedFrameWithNoFaces(),
            currentPreviewTextureId = 11,
            analysisPreviewTextureId = 22,
            previousPreviewTextureId = 33,
            wasSynchronized = true,
        )

        assertEquals(11, selection.textureId)
        assertFalse(selection.isSynchronized)
    }

    @Test
    fun `녹화 중이고 분석 프레임이 있으면 프리뷰는 분석 완료 프레임으로 전환된다`() {
        val selection = resolvePreviewFrameSelection(
            recordingEnabled = true,
            processedFrame = processedFrameWithNoFaces(),
            currentPreviewTextureId = 11,
            analysisPreviewTextureId = 22,
            previousPreviewTextureId = 33,
            wasSynchronized = false,
        )

        assertEquals(22, selection.textureId)
        assertTrue(selection.isSynchronized)
    }

    @Test
    fun `동기화가 이미 성립된 뒤 분석 프레임이 잠시 비면 이전 동기화 프레임을 유지한다`() {
        val selection = resolvePreviewFrameSelection(
            recordingEnabled = true,
            processedFrame = null,
            currentPreviewTextureId = 11,
            analysisPreviewTextureId = 22,
            previousPreviewTextureId = 33,
            wasSynchronized = true,
        )

        assertEquals(33, selection.textureId)
        assertTrue(selection.isSynchronized)
    }

    @Test
    fun `녹화 시작 직후 아직 분석 프레임이 없으면 현재 프레임을 유지한다`() {
        val selection = resolvePreviewFrameSelection(
            recordingEnabled = true,
            processedFrame = null,
            currentPreviewTextureId = 11,
            analysisPreviewTextureId = 22,
            previousPreviewTextureId = 0,
            wasSynchronized = false,
        )

        assertEquals(11, selection.textureId)
        assertFalse(selection.isSynchronized)
    }

    private fun processedFrameWithNoFaces(): ProcessedFrame = ProcessedFrame(
        faces = emptyList(),
        frameWidth = 1280,
        frameHeight = 720,
        timestampMs = 1000L
    )
}
