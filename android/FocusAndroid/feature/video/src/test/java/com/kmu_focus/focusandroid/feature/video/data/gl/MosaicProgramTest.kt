package com.kmu_focus.focusandroid.core.media.data.gl

import org.junit.Assert.assertEquals
import org.junit.Test

class MosaicProgramTest {

    @Test
    fun `ellipse 목록이 재사용 uniform 배열에 올바르게 반영된다`() {
        val program = MosaicProgram()
        val ellipses = listOf(
            EllipseParams(
                centerX = 0.10f,
                centerY = 0.20f,
                radiusX = 0.30f,
                radiusY = 0.40f,
                angle = 0.50f
            ),
            EllipseParams(
                centerX = 0.60f,
                centerY = 0.70f,
                radiusX = 0.80f,
                radiusY = 0.90f,
                angle = 1.00f
            )
        )

        val faceCount = program.updateUniformData(ellipses)
        val centers = program.getUniformCentersForTest()
        val radii = program.getUniformRadiiForTest()
        val angles = program.getUniformAnglesForTest()

        assertEquals(2, faceCount)
        assertEquals(0.10f, centers[0], EPSILON)
        assertEquals(0.20f, centers[1], EPSILON)
        assertEquals(0.60f, centers[2], EPSILON)
        assertEquals(0.70f, centers[3], EPSILON)

        assertEquals(0.30f, radii[0], EPSILON)
        assertEquals(0.40f, radii[1], EPSILON)
        assertEquals(0.80f, radii[2], EPSILON)
        assertEquals(0.90f, radii[3], EPSILON)

        assertEquals(0.50f, angles[0], EPSILON)
        assertEquals(1.00f, angles[1], EPSILON)
        assertEquals(0f, centers[4], EPSILON)
        assertEquals(0f, radii[4], EPSILON)
        assertEquals(0f, angles[2], EPSILON)
    }

    @Test
    fun `blockSize 픽셀 값이 뷰 크기 기준으로 정규화된다`() {
        val normalizedX = MosaicProgram.normalizeBlockSizeX(blockPixels = 16f, viewWidth = 1920)
        val normalizedY = MosaicProgram.normalizeBlockSizeY(blockPixels = 16f, viewHeight = 1080)

        assertEquals(16f / 1920f, normalizedX, EPSILON)
        assertEquals(16f / 1080f, normalizedY, EPSILON)
    }

    @Test
    fun `faceCount는 MAX_FACES를 넘지 않는다`() {
        val program = MosaicProgram()
        val ellipses = (0 until 12).map { index ->
            EllipseParams(
                centerX = 0.01f * index,
                centerY = 0.02f * index,
                radiusX = 0.03f,
                radiusY = 0.04f,
                angle = 0.05f * index
            )
        }

        val faceCount = program.updateUniformData(ellipses)

        assertEquals(MosaicProgram.MAX_FACES, faceCount)
    }

    @Test
    fun `이전 프레임 데이터는 faceCount 감소 시 0으로 초기화된다`() {
        val program = MosaicProgram()
        program.updateUniformData(
            listOf(
                EllipseParams(0.1f, 0.2f, 0.3f, 0.4f, 0.5f),
                EllipseParams(0.6f, 0.7f, 0.8f, 0.9f, 1.0f)
            )
        )

        val faceCount = program.updateUniformData(
            listOf(EllipseParams(0.11f, 0.22f, 0.33f, 0.44f, 0.55f))
        )
        val centers = program.getUniformCentersForTest()
        val radii = program.getUniformRadiiForTest()
        val angles = program.getUniformAnglesForTest()

        assertEquals(1, faceCount)
        assertEquals(0f, centers[2], EPSILON)
        assertEquals(0f, centers[3], EPSILON)
        assertEquals(0f, radii[2], EPSILON)
        assertEquals(0f, radii[3], EPSILON)
        assertEquals(0f, angles[1], EPSILON)
    }

    private companion object {
        private const val EPSILON = 0.0001f
    }
}
