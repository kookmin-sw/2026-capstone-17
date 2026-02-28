package com.kmu_focus.focusandroid.feature.camera.domain.repository

import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import java.nio.ByteBuffer

interface CameraAnalysisRepository {
    fun processFrame(
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        timestampMs: Long,
    ): ProcessedFrame

    fun clearProcessingThreadCache()

    fun startMetadataSession()

    suspend fun closeMetadataSession()
}
