package com.kmu_focus.focusandroid.feature.camera.domain.repository

import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import com.kmu_focus.focusandroid.feature.camera.domain.entity.OwnerRegistrationResult
import java.nio.ByteBuffer

interface CameraAnalysisRepository {
    fun processFrame(
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        timestampMs: Long,
    ): ProcessedFrame

    fun registerOwnerFromFrame(
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        trackId: Int,
        processedFrame: ProcessedFrame,
    ): OwnerRegistrationResult

    fun clearProcessingThreadCache()

    fun startMetadataSession()

    suspend fun closeMetadataSession()
}
