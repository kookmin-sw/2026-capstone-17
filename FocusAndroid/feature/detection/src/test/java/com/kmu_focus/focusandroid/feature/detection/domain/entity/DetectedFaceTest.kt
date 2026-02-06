package com.kmu_focus.focusandroid.feature.detection.domain.entity

import org.junit.Assert.*
import org.junit.Test

class DetectedFaceTest {

    @Test
    fun `기본 생성 시 landmarks가 null임`() {
        val face = DetectedFace(x = 10, y = 20, width = 100, height = 120, confidence = 0.95f)
        assertNull(face.landmarks)
    }

    @Test
    fun `모든 필드가 올바르게 생성됨`() {
        val landmarks = FaceLandmarks5(
            rightEye = Point2f(30f, 40f),
            leftEye = Point2f(70f, 40f),
            nose = Point2f(50f, 60f),
            rightMouth = Point2f(35f, 80f),
            leftMouth = Point2f(65f, 80f)
        )
        val face = DetectedFace(
            x = 10, y = 20, width = 100, height = 120,
            confidence = 0.95f, landmarks = landmarks
        )

        assertEquals(10, face.x)
        assertEquals(20, face.y)
        assertEquals(100, face.width)
        assertEquals(120, face.height)
        assertEquals(0.95f, face.confidence, 0.001f)
        assertNotNull(face.landmarks)
    }

    @Test
    fun `data class 동등성 비교가 작동함`() {
        val face1 = DetectedFace(x = 10, y = 20, width = 100, height = 120, confidence = 0.9f)
        val face2 = DetectedFace(x = 10, y = 20, width = 100, height = 120, confidence = 0.9f)
        assertEquals(face1, face2)
        assertEquals(face1.hashCode(), face2.hashCode())
    }

    @Test
    fun `copy를 통한 필드 변경이 작동함`() {
        val face = DetectedFace(x = 10, y = 20, width = 100, height = 120, confidence = 0.9f)
        val copied = face.copy(confidence = 0.5f)
        assertEquals(0.5f, copied.confidence, 0.001f)
        assertEquals(10, copied.x)
    }
}
