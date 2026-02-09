package com.kmu_focus.focusandroid.feature.video.domain.entity

import org.junit.Assert.*
import org.junit.Test

class VideoExportDataTest {

    @Test
    fun `FrameExport toJsonObject에 3dmm id_coeffs exp_coeffs pose 포함`() {
        val frame = FrameExport(
            frameNumber = 1,
            timestamp = 1.5,
            faces = listOf(
                FaceExport(
                    trackingId = 0,
                    bbox = intArrayOf(10, 20, 100, 120),
                    idCoeffs = floatArrayOf(0.1f, 0.2f),
                    expCoeffs = floatArrayOf(0.3f),
                    pose = floatArrayOf(0.4f, 0.5f)
                )
            )
        )
        val json = frame.toJsonObject()
        assertEquals(1, json.getInt("frame_number"))
        assertEquals(1.5, json.getDouble("timestamp"), 0.001)
        val faces = json.getJSONArray("faces")
        assertEquals(1, faces.length())
        val face = faces.getJSONObject(0)
        assertEquals(0, face.getInt("tracking_id"))
        val bbox = face.getJSONArray("bbox")
        assertEquals(10, bbox.getInt(0))
        assertEquals(100, bbox.getInt(2))
        val d3 = face.getJSONObject("3dmm")
        val idCoeffs = d3.getJSONArray("id_coeffs")
        assertEquals(2, idCoeffs.length())
        assertEquals(0.1, idCoeffs.getDouble(0), 0.001)
        assertEquals(0.3, d3.getJSONArray("exp_coeffs").getDouble(0), 0.001)
        assertEquals(0.4, d3.getJSONArray("pose").getDouble(0), 0.001)
    }

    @Test
    fun `isOwner가 true인 얼굴은 toJsonObject faces에서 제외됨`() {
        val frame = FrameExport(
            frameNumber = 0,
            timestamp = 0.0,
            faces = listOf(
                FaceExport(trackingId = 0, bbox = intArrayOf(0, 0, 50, 50), isOwner = true),
                FaceExport(trackingId = 1, bbox = intArrayOf(100, 0, 50, 50), isOwner = false)
            )
        )
        val json = frame.toJsonObject()
        val faces = json.getJSONArray("faces")
        assertEquals(1, faces.length())
        assertEquals(1, faces.getJSONObject(0).getInt("tracking_id"))
    }

    @Test
    fun `VideoExportData toJsonObject에 video_info와 frames 포함`() {
        val data = VideoExportData(
            videoInfo = VideoInfo(640, 480, 30f),
            frames = listOf(
                FrameExport(0, 0.0, emptyList())
            )
        )
        val json = data.toJsonObject()
        val info = json.getJSONObject("video_info")
        assertEquals(640, info.getInt("width"))
        assertEquals(480, info.getInt("height"))
        assertEquals(30.0, info.getDouble("fps"), 0.001)
        assertEquals("3dmm", info.getString("format"))
        assertEquals(1, json.getJSONArray("frames").length())
    }
}
