package com.kmu_focus.focusandroid.core.grpc.data.mapper

import com.kmu_focus.focusandroid.core.grpc.proto.BoundingBox
import com.kmu_focus.focusandroid.core.grpc.proto.FaceEntry
import com.kmu_focus.focusandroid.core.grpc.proto.PushFaceMetadataRequest
import com.kmu_focus.focusandroid.core.grpc.proto.TdmmRaw
import com.kmu_focus.focusandroid.core.metadata.domain.entity.FrameMetadata

object GrpcMetadataMapper {
    fun toProto(metadata: FrameMetadata): PushFaceMetadataRequest {
        return PushFaceMetadataRequest.newBuilder()
            .setSessionId(metadata.sessionId)
            .setPtsUs(metadata.ptsUs)
            .addAllFaces(
                metadata.faces.map { face ->
                    FaceEntry.newBuilder()
                        .setTrackingId(face.trackingId)
                        .setBbox(
                            BoundingBox.newBuilder()
                                .setX(face.bbox.x)
                                .setY(face.bbox.y)
                                .setWidth(face.bbox.width)
                                .setHeight(face.bbox.height)
                                .build()
                        )
                        .setTdmmRaw(
                            TdmmRaw.newBuilder()
                                .addAllCoeffs(face.tdmm.coeffs.toList())
                                .build()
                        )
                        .build()
                }
            )
            .build()
    }
}
