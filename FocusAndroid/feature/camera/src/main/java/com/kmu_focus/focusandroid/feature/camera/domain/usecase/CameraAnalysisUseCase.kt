package com.kmu_focus.focusandroid.feature.camera.domain.usecase

import com.kmu_focus.focusandroid.feature.camera.domain.entity.OwnerRegistrationResult
import com.kmu_focus.focusandroid.feature.camera.domain.repository.CameraAnalysisRepository
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import java.nio.ByteBuffer
import javax.inject.Inject

class CameraAnalysisUseCase @Inject constructor(
    private val cameraAnalysisRepository: CameraAnalysisRepository,
) {
    fun processFrame(
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        timestampMs: Long,
    ): ProcessedFrame = cameraAnalysisRepository.processFrame(
        rgbaBuffer = rgbaBuffer,
        width = width,
        height = height,
        timestampMs = timestampMs,
    )

    fun registerOwnerFromFrame(
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        trackId: Int,
        processedFrame: ProcessedFrame,
    ): OwnerRegistrationResult = cameraAnalysisRepository.registerOwnerFromFrame(
        rgbaBuffer = rgbaBuffer,
        width = width,
        height = height,
        trackId = trackId,
        processedFrame = processedFrame,
    )

    fun clearProcessingThreadCache() {
        cameraAnalysisRepository.clearProcessingThreadCache()
    }

    fun startMetadataSession() {
        cameraAnalysisRepository.startMetadataSession()
    }

    suspend fun closeMetadataSession() {
        cameraAnalysisRepository.closeMetadataSession()
    }
}
