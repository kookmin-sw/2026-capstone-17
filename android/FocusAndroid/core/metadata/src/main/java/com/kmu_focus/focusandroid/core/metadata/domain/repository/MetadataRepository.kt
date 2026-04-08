package com.kmu_focus.focusandroid.core.metadata.domain.repository

import com.kmu_focus.focusandroid.core.metadata.domain.entity.FrameMetadata

interface MetadataRepository {
    suspend fun sendFrame(metadata: FrameMetadata)
    suspend fun close()
}
