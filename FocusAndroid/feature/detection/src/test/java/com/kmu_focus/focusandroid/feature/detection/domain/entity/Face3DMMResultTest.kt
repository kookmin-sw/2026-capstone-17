package com.kmu_focus.focusandroid.feature.detection.domain.entity

import org.junit.Assert.*
import org.junit.Test

class Face3DMMResultTest {

    @Test
    fun `getAbsoluteVertices - 정규화 좌표를 faceRect 기준 절대 좌표로 변환`() {
        val faceRect = FaceRect(100, 50, 200, 150)
        val result = Face3DMMResult(
            vertices = listOf(Vertex2D(0.5f, 0.5f), Vertex2D(0f, 0f)),
            faceRect = faceRect
        )
        val absolute = result.getAbsoluteVertices()
        assertEquals(2, absolute.size)
        assertEquals(150f, absolute[0].x, 0.001f)
        assertEquals(100f, absolute[0].y, 0.001f)
        assertEquals(100f, absolute[1].x, 0.001f)
        assertEquals(50f, absolute[1].y, 0.001f)
    }

    @Test
    fun `getVertices3DForExport - rawVertices3D 우선 반환`() {
        val faceRect = FaceRect(0, 0, 100, 100)
        val raw3d = listOf(Vertex3D(0, 1f, 2f, 3f))
        val result = Face3DMMResult(
            vertices = listOf(Vertex2D(0.1f, 0.2f)),
            faceRect = faceRect,
            rawVertices3D = raw3d
        )
        val export = result.getVertices3DForExport()
        assertEquals(1, export.size)
        assertEquals(1f, export[0].x, 0.001f)
        assertEquals(3f, export[0].z, 0.001f)
    }

    @Test
    fun `getVertices3DForExport - raw 없으면 2D+z=0`() {
        val faceRect = FaceRect(10, 20, 110, 120)
        val result = Face3DMMResult(
            vertices = listOf(Vertex2D(0.5f, 0.5f)),
            faceRect = faceRect
        )
        val export = result.getVertices3DForExport()
        assertEquals(1, export.size)
        assertEquals(60f, export[0].x, 0.001f)
        assertEquals(70f, export[0].y, 0.001f)
        assertEquals(0f, export[0].z, 0.001f)
    }
}
