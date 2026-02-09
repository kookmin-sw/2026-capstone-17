package com.kmu_focus.focusandroid.core.ai.domain.entity

import org.junit.Assert.*
import org.junit.Test

class FaceLandmarks5Test {

    private val landmarks = FaceLandmarks5(
        rightEye = Point2f(30f, 40f),
        leftEye = Point2f(70f, 40f),
        nose = Point2f(50f, 60f),
        rightMouth = Point2f(35f, 80f),
        leftMouth = Point2f(65f, 80f)
    )

    @Test
    fun `Point2f 동등성 비교가 작동함`() {
        val p1 = Point2f(1.5f, 2.5f)
        val p2 = Point2f(1.5f, 2.5f)
        assertEquals(p1, p2)
        assertEquals(p1.hashCode(), p2.hashCode())
    }

    @Test
    fun `getEyeCenter가 두 눈의 중점을 반환함`() {
        val center = landmarks.getEyeCenter()
        assertEquals(50f, center.x, 0.001f)
        assertEquals(40f, center.y, 0.001f)
    }

    @Test
    fun `getEyeDistance가 유클리드 거리를 반환함`() {
        val distance = landmarks.getEyeDistance()
        assertEquals(40f, distance, 0.001f)
    }

    @Test
    fun `getMouthCenter가 입 양 끝의 중점을 반환함`() {
        val center = landmarks.getMouthCenter()
        assertEquals(50f, center.x, 0.001f)
        assertEquals(80f, center.y, 0.001f)
    }

    @Test
    fun `getFaceAngle이 수평일 때 0 라디안을 반환함`() {
        val angle = landmarks.getFaceAngle()
        assertEquals(0f, angle, 0.001f)
    }

    @Test
    fun `getFaceAngle이 기울어진 얼굴에 대해 라디안 각도를 반환함`() {
        val tilted = FaceLandmarks5(
            rightEye = Point2f(30f, 50f),
            leftEye = Point2f(70f, 40f),
            nose = Point2f(50f, 60f),
            rightMouth = Point2f(35f, 80f),
            leftMouth = Point2f(65f, 80f)
        )
        val angle = tilted.getFaceAngle()
        assertTrue(angle < 0f)
    }

    @Test
    fun `isFrontal이 대칭일 때 true를 반환함`() {
        assertTrue(landmarks.isFrontal())
    }

    @Test
    fun `isFrontal이 코가 눈 중심에서 벗어나면 false를 반환함`() {
        val asymmetric = FaceLandmarks5(
            rightEye = Point2f(20f, 40f),
            leftEye = Point2f(60f, 40f),
            nose = Point2f(90f, 60f),
            rightMouth = Point2f(35f, 80f),
            leftMouth = Point2f(65f, 80f)
        )
        assertFalse(asymmetric.isFrontal(0.2f))
    }

    @Test
    fun `isFrontal symmetryThreshold가 적용됨`() {
        val slightlyOff = FaceLandmarks5(
            rightEye = Point2f(20f, 40f),
            leftEye = Point2f(60f, 40f),
            nose = Point2f(44f, 60f),
            rightMouth = Point2f(35f, 80f),
            leftMouth = Point2f(65f, 80f)
        )
        assertTrue(slightlyOff.isFrontal(0.2f))
        assertFalse(slightlyOff.isFrontal(0.05f))
    }
}
