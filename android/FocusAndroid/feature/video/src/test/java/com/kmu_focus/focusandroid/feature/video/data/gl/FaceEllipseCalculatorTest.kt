package com.kmu_focus.focusandroid.core.media.data.gl

import com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.core.ai.domain.entity.FaceLandmarks5
import com.kmu_focus.focusandroid.core.ai.domain.entity.Point2f
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceEllipseCalculatorTest {

    @Test
    fun `얼굴이 없으면 빈 리스트를 반환한다`() {
        val frame = ProcessedFrame(
            faces = emptyList(),
            frameWidth = 200,
            frameHeight = 200,
            timestampMs = 1000L
        )

        val result = FaceEllipseCalculator.calculate(frame)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `landmarks가 없어도 박스 기준으로 마스크를 생성한다`() {
        val frame = ProcessedFrame(
            faces = listOf(
                DetectedFace(
                    x = 10,
                    y = 10,
                    width = 100,
                    height = 100,
                    confidence = 0.9f,
                    landmarks = null
                )
            ),
            frameWidth = 200,
            frameHeight = 200,
            timestampMs = 1000L,
            faceLabels = listOf(false)
        )

        val result = FaceEllipseCalculator.calculate(frame)

        assertEquals(1, result.size)
    }

    @Test
    fun `OWNER는 제외하고 OTHER와 PENDING은 포함한다`() {
        val frame = ProcessedFrame(
            faces = listOf(
                faceBox(x = 20, y = 20), // OWNER
                faceBox(x = 50, y = 30), // OTHER
                faceBox(x = 80, y = 40)  // PENDING
            ),
            frameWidth = 200,
            frameHeight = 200,
            timestampMs = 1000L,
            faceLabels = listOf(true, false, null)
        )

        val result = FaceEllipseCalculator.calculate(frame)

        assertEquals(2, result.size)
    }

    @Test
    fun `랜드마크가 있으면 기존 랜드마크 기반 타원을 사용한다`() {
        val frame = ProcessedFrame(
            faces = listOf(
                DetectedFace(
                    x = 20,
                    y = 30,
                    width = 100,
                    height = 80,
                    confidence = 0.9f,
                    landmarks = FaceLandmarks5(
                        rightEye = Point2f(40f, 40f),
                        leftEye = Point2f(80f, 40f),
                        nose = Point2f(60f, 58f),
                        rightMouth = Point2f(50f, 90f),
                        leftMouth = Point2f(70f, 90f),
                    ),
                )
            ),
            frameWidth = 200,
            frameHeight = 200,
            timestampMs = 1000L,
            faceLabels = listOf(false)
        )

        val result = FaceEllipseCalculator.calculate(frame)

        assertEquals(1, result.size)
        val ellipse = result.first()
        assertEquals(0.30f, ellipse.centerX, EPSILON)
        assertEquals(0.275f, ellipse.centerY, EPSILON)
        assertEquals(0.224f, ellipse.radiusX, EPSILON)
        assertEquals(0.378f, ellipse.radiusY, EPSILON)
        assertEquals(0f, ellipse.angle, EPSILON)
    }

    @Test
    fun `타원 결과는 최대 8개까지만 반환한다`() {
        val faces = (0 until 10).map { index ->
            faceBox(
                x = 30 + index,
                y = 30 + index,
            )
        }
        val frame = ProcessedFrame(
            faces = faces,
            frameWidth = 400,
            frameHeight = 400,
            timestampMs = 1000L,
            faceLabels = List(10) { false }
        )

        val result = FaceEllipseCalculator.calculate(frame)

        assertEquals(8, result.size)
    }

    private fun faceBox(
        x: Int,
        y: Int,
    ): DetectedFace {
        return DetectedFace(
            x = x,
            y = y,
            width = 80,
            height = 80,
            confidence = 0.9f,
        )
    }

    private companion object {
        private const val EPSILON = 0.0001f
    }
}
