package com.kmu_focus.focusandroid.core.grpc.data.mapper

import com.kmu_focus.focusandroid.core.metadata.domain.entity.BBox
import com.kmu_focus.focusandroid.core.metadata.domain.entity.FaceData
import com.kmu_focus.focusandroid.core.metadata.domain.entity.FrameMetadata
import com.kmu_focus.focusandroid.core.metadata.domain.entity.ThreeDMM
import org.junit.Assert.assertEquals
import org.junit.Test

class GrpcMetadataMapperTest {

    @Test
    fun `FrameMetadata의 sessionId가 proto의 session_id로 매핑된다`() {
        val metadata = FrameMetadata(
            sessionId = "broadcast-123",
            ptsUs = 100_000L,
            faces = emptyList(),
        )

        val proto = GrpcMetadataMapper.toProto(metadata)

        assertEquals("broadcast-123", proto.sessionId)
    }

    @Test
    fun `FrameMetadata의 ptsUs가 proto의 pts_us로 매핑된다`() {
        val metadata = FrameMetadata(
            sessionId = "broadcast-123",
            ptsUs = 133_333L,
            faces = emptyList(),
        )

        val proto = GrpcMetadataMapper.toProto(metadata)

        assertEquals(133_333L, proto.ptsUs)
    }

    @Test
    fun `FaceData의 trackingId가 proto에 매핑된다`() {
        val metadata = FrameMetadata(
            sessionId = "broadcast-123",
            ptsUs = 100_000L,
            faces = listOf(
                FaceData(
                    trackingId = 42,
                    bbox = BBox(x = 100, y = 200, width = 50, height = 60),
                    tdmm = ThreeDMM(coeffs = floatArrayOf(0.1f, 0.2f, 0.3f)),
                ),
            ),
        )

        val proto = GrpcMetadataMapper.toProto(metadata)

        assertEquals(1, proto.facesCount)
        assertEquals(42, proto.getFaces(0).trackingId)
    }

    @Test
    fun `BBox가 proto의 BoundingBox로 매핑된다`() {
        val metadata = FrameMetadata(
            sessionId = "broadcast-123",
            ptsUs = 100_000L,
            faces = listOf(
                FaceData(
                    trackingId = 0,
                    bbox = BBox(x = 659, y = 177, width = 49, height = 64),
                    tdmm = ThreeDMM(coeffs = floatArrayOf()),
                ),
            ),
        )

        val proto = GrpcMetadataMapper.toProto(metadata)
        val bbox = proto.getFaces(0).bbox

        assertEquals(659, bbox.x)
        assertEquals(177, bbox.y)
        assertEquals(49, bbox.width)
        assertEquals(64, bbox.height)
    }

    @Test
    fun `ThreeDMM coeffs가 proto의 tdmm_raw coeffs로 매핑된다`() {
        val coeffs = FloatArray(265) { it * 0.01f }
        val metadata = FrameMetadata(
            sessionId = "broadcast-123",
            ptsUs = 100_000L,
            faces = listOf(
                FaceData(
                    trackingId = 0,
                    bbox = BBox(x = 0, y = 0, width = 100, height = 100),
                    tdmm = ThreeDMM(coeffs = coeffs),
                ),
            ),
        )

        val proto = GrpcMetadataMapper.toProto(metadata)
        val protoCoeffs = proto.getFaces(0).tdmmRaw.coeffsList

        assertEquals(265, protoCoeffs.size)
        assertEquals(0.0f, protoCoeffs[0], 0.001f)
        assertEquals(2.64f, protoCoeffs[264], 0.001f)
    }

    @Test
    fun `여러 얼굴이 모두 proto에 매핑된다`() {
        val metadata = FrameMetadata(
            sessionId = "broadcast-123",
            ptsUs = 100_000L,
            faces = listOf(
                FaceData(
                    trackingId = 0,
                    bbox = BBox(x = 10, y = 20, width = 30, height = 40),
                    tdmm = ThreeDMM(coeffs = floatArrayOf(0.1f)),
                ),
                FaceData(
                    trackingId = 1,
                    bbox = BBox(x = 100, y = 200, width = 50, height = 60),
                    tdmm = ThreeDMM(coeffs = floatArrayOf(0.2f)),
                ),
            ),
        )

        val proto = GrpcMetadataMapper.toProto(metadata)

        assertEquals(2, proto.facesCount)
        assertEquals(0, proto.getFaces(0).trackingId)
        assertEquals(1, proto.getFaces(1).trackingId)
    }

    @Test
    fun `빈 faces 리스트도 정상 매핑된다`() {
        val metadata = FrameMetadata(
            sessionId = "broadcast-123",
            ptsUs = 100_000L,
            faces = emptyList(),
        )

        val proto = GrpcMetadataMapper.toProto(metadata)

        assertEquals(0, proto.facesCount)
    }
}
