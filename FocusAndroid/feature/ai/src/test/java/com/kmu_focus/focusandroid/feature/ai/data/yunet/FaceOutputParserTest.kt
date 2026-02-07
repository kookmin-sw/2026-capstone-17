package com.kmu_focus.focusandroid.feature.ai.data.yunet

import org.junit.Assert.*
import org.junit.Test

class FaceOutputParserTest {

    @Test
    fun `빈 입력에서 빈 리스트를 반환함`() {
        val result = parseFaceOutput(emptyList(), 1f, 1f)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `단일 얼굴을 올바르게 파싱함`() {
        val row = floatArrayOf(
            10f, 20f, 100f, 120f,
            30f, 40f, 70f, 40f, 50f, 60f, 35f, 80f, 65f, 80f,
            0.95f
        )
        val result = parseFaceOutput(listOf(row), 1f, 1f)

        assertEquals(1, result.size)
        val face = result[0]
        assertEquals(10, face.x)
        assertEquals(20, face.y)
        assertEquals(100, face.width)
        assertEquals(120, face.height)
        assertEquals(0.95f, face.confidence, 0.001f)
    }

    @Test
    fun `스케일 팩터가 좌표에 올바르게 적용됨`() {
        val row = floatArrayOf(
            10f, 20f, 50f, 60f,
            15f, 25f, 35f, 25f, 25f, 35f, 18f, 45f, 33f, 45f,
            0.9f
        )
        val result = parseFaceOutput(listOf(row), 2f, 3f)

        val face = result[0]
        assertEquals(20, face.x)
        assertEquals(60, face.y)
        assertEquals(100, face.width)
        assertEquals(180, face.height)
    }

    @Test
    fun `랜드마크가 올바르게 매핑됨`() {
        val row = floatArrayOf(
            0f, 0f, 100f, 100f,
            30f, 40f, 70f, 40f, 50f, 60f, 35f, 80f, 65f, 80f,
            0.9f
        )
        val result = parseFaceOutput(listOf(row), 1f, 1f)
        val lm = result[0].landmarks!!

        assertEquals(30f, lm.rightEye.x, 0.001f)
        assertEquals(40f, lm.rightEye.y, 0.001f)
        assertEquals(70f, lm.leftEye.x, 0.001f)
        assertEquals(50f, lm.nose.x, 0.001f)
        assertEquals(60f, lm.nose.y, 0.001f)
        assertEquals(35f, lm.rightMouth.x, 0.001f)
        assertEquals(65f, lm.leftMouth.x, 0.001f)
    }

    @Test
    fun `다수의 얼굴을 올바르게 파싱함`() {
        val row1 = floatArrayOf(0f, 0f, 50f, 50f, 10f, 10f, 20f, 10f, 15f, 15f, 10f, 20f, 20f, 20f, 0.9f)
        val row2 = floatArrayOf(100f, 100f, 60f, 70f, 110f, 110f, 130f, 110f, 120f, 120f, 110f, 130f, 130f, 130f, 0.8f)

        val result = parseFaceOutput(listOf(row1, row2), 1f, 1f)

        assertEquals(2, result.size)
        assertEquals(0, result[0].x)
        assertEquals(100, result[1].x)
        assertEquals(0.9f, result[0].confidence, 0.001f)
        assertEquals(0.8f, result[1].confidence, 0.001f)
    }
}
