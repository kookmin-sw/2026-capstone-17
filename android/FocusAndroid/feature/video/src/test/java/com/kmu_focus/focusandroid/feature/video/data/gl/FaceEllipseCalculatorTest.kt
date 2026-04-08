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
    fun `landmarks가 null인 얼굴은 스킵한다`() {
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

        assertTrue(result.isEmpty())
    }

    @Test
    fun `OWNER는 제외하고 OTHER와 PENDING은 포함한다`() {
        val frame = ProcessedFrame(
            faces = listOf(
                faceWithLandmarks(rightEyeX = 20f, rightEyeY = 20f), // OWNER
                faceWithLandmarks(rightEyeX = 50f, rightEyeY = 30f), // OTHER
                faceWithLandmarks(rightEyeX = 80f, rightEyeY = 40f)  // PENDING
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
    fun `정규화 좌표와 반경 그리고 각도가 계산식과 일치한다`() {
        val landmarks = FaceLandmarks5(
            rightEye = Point2f(40f, 60f),
            leftEye = Point2f(80f, 60f),
            nose = Point2f(60f, 75f),
            rightMouth = Point2f(50f, 100f),
            leftMouth = Point2f(70f, 100f)
        )
        val frame = ProcessedFrame(
            faces = listOf(
                DetectedFace(
                    x = 20,
                    y = 20,
                    width = 100,
                    height = 100,
                    confidence = 0.9f,
                    landmarks = landmarks
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
        assertEquals(0.3f, ellipse.centerX, EPSILON)
        assertEquals(0.36f, ellipse.centerY, EPSILON)
        assertEquals(0.21f, ellipse.radiusX, EPSILON)
        assertEquals(0.2835f, ellipse.radiusY, EPSILON)
        assertEquals(landmarks.getFaceAngle(), ellipse.angle, EPSILON)
    }

    @Test
    fun `타원 결과는 최대 8개까지만 반환한다`() {
        val faces = (0 until 10).map { index ->
            faceWithLandmarks(
                rightEyeX = 30f + index,
                rightEyeY = 30f + index
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

    private fun faceWithLandmarks(
        rightEyeX: Float,
        rightEyeY: Float
    ): DetectedFace {
        val leftEyeX = rightEyeX + 40f
        val leftEyeY = rightEyeY
        val mouthY = rightEyeY + 40f
        return DetectedFace(
            x = rightEyeX.toInt(),
            y = rightEyeY.toInt(),
            width = 80,
            height = 80,
            confidence = 0.9f,
            landmarks = FaceLandmarks5(
                rightEye = Point2f(rightEyeX, rightEyeY),
                leftEye = Point2f(leftEyeX, leftEyeY),
                nose = Point2f((rightEyeX + leftEyeX) / 2f, rightEyeY + 15f),
                rightMouth = Point2f(rightEyeX + 10f, mouthY),
                leftMouth = Point2f(leftEyeX - 10f, mouthY)
            )
        )
    }

    private companion object {
        private const val EPSILON = 0.0001f
    }
}
