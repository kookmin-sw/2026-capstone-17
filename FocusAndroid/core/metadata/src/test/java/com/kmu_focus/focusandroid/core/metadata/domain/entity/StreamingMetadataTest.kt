package com.kmu_focus.focusandroid.core.metadata.domain.entity

import org.junit.Assert.*
import org.junit.Test

class StreamingMetadataTest {

    @Test
    fun `FrameMetadata 생성 시 필드가 올바르게 설정된다`() {
        val face = FaceData(
            trackingId = 0,
            bbox = BBox(x = 659, y = 177, width = 49, height = 64),
            tdmm = ThreeDMM(
                format = ThreeDMMFormat.ID80_EXP64_POSE6_V1,
                modelVersion = "3dmm-v1",
                coeffs = FloatArray(150) { 0.1f },
                idDim = 80,
                expDim = 64,
                poseDim = 6,
            ),
        )

        val metadata = FrameMetadata(
            sessionId = "abc-123",
            ptsUs = 133333L,
            faces = listOf(face),
        )

        assertEquals("abc-123", metadata.sessionId)
        assertEquals(133333L, metadata.ptsUs)
        assertEquals(1, metadata.faces.size)
        assertEquals(0, metadata.faces[0].trackingId)
        assertEquals(ThreeDMMFormat.ID80_EXP64_POSE6_V1, metadata.faces[0].tdmm.format)
        assertEquals("3dmm-v1", metadata.faces[0].tdmm.modelVersion)
        assertEquals(150, metadata.faces[0].tdmm.coeffs.size)
        assertEquals(80, metadata.faces[0].tdmm.idDim)
        assertEquals(64, metadata.faces[0].tdmm.expDim)
        assertEquals(6, metadata.faces[0].tdmm.poseDim)
        assertEquals(0, metadata.faces[0].tdmm.extraDim)
    }

    @Test
    fun `얼굴 미검출 프레임은 faces가 빈 리스트`() {
        val metadata = FrameMetadata(
            sessionId = "abc-123",
            ptsUs = 0L,
            faces = emptyList(),
        )

        assertTrue(metadata.faces.isEmpty())
    }

    @Test
    fun `BBox 좌표가 정확히 저장된다`() {
        val bbox = BBox(x = 100, y = 200, width = 50, height = 60)

        assertEquals(100, bbox.x)
        assertEquals(200, bbox.y)
        assertEquals(50, bbox.width)
        assertEquals(60, bbox.height)
    }
}
