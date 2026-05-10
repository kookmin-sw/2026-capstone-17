package com.kmu_focus.focusandroid.core.grpc.data.repository

import com.kmu_focus.focusandroid.core.grpc.data.mapper.GrpcMetadataMapper
import com.kmu_focus.focusandroid.core.grpc.data.remote.FaceMetadataStreamManager
import com.kmu_focus.focusandroid.core.metadata.domain.entity.FrameMetadata
import com.kmu_focus.focusandroid.core.metadata.domain.repository.MetadataRepository
import javax.inject.Inject

class GrpcMetadataRepositoryImpl @Inject constructor(
    private val streamManager: FaceMetadataStreamManager,
) : MetadataRepository {
    override suspend fun sendFrame(metadata: FrameMetadata) {
        streamManager.sendFrame(GrpcMetadataMapper.toProto(metadata))
    }

    override suspend fun close() {
        streamManager.complete()
    }
}
