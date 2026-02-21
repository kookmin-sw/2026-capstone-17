package com.kmu_focus.focusandroid.core.metadata.domain.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataMapperTest {

    @Test
    fun `isFaceMapLayout과 맞지 않으면 face가 드롭된다`() {
        val frame = MetadataMapper.mapFrame(
            sessionId = "session-1",
            timestampSeconds = 1.0,
            faces = listOf(
                MetadataMapper.FaceExportPayload(
                    trackingId = 10,
                    bbox = intArrayOf(100, 120, 60, 70),
                    idCoeffs = FloatArray(80) { 0.1f }, // invalid: expected 219
                    expCoeffs = FloatArray(39) { 0.2f },
                    pose = FloatArray(6) { 0.3f },
                    extraCoeffs = FloatArray(1) { 0.4f },
                    isOwner = false,
                )
            ),
        )

        assertTrue(frame.faces.isEmpty())
    }

    @Test
    fun `유효한 FaceMap 레이아웃은 face에 포함된다`() {
        val frame = MetadataMapper.mapFrame(
            sessionId = "session-1",
            timestampSeconds = 1.234567,
            faces = listOf(
                MetadataMapper.FaceExportPayload(
                    trackingId = 11,
                    bbox = intArrayOf(10, 20, 30, 40),
                    idCoeffs = FloatArray(219) { 0.1f },
                    expCoeffs = FloatArray(39) { 0.2f },
                    pose = FloatArray(6) { 0.3f },
                    extraCoeffs = FloatArray(1) { 0.4f },
                    isOwner = false,
                )
            ),
        )

        assertEquals(1, frame.faces.size)
        assertEquals(11, frame.faces.first().trackingId)
        assertEquals(265, frame.faces.first().tdmm.coeffs.size)
        assertEquals(1_234_567L, frame.ptsUs)
    }
}
