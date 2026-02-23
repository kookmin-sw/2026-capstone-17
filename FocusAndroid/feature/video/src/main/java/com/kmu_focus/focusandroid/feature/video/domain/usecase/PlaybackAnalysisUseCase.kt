package com.kmu_focus.focusandroid.feature.video.domain.usecase

import com.kmu_focus.focusandroid.feature.video.domain.entity.ProcessedFrame
import com.kmu_focus.focusandroid.feature.video.domain.repository.PlaybackAnalysisRepository
import java.nio.ByteBuffer
import javax.inject.Inject

class PlaybackAnalysisUseCase @Inject constructor(
    private val playbackAnalysisRepository: PlaybackAnalysisRepository,
) {
    fun processFrame(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        timestampMs: Long,
        frameIndex: Int?,
    ): ProcessedFrame = playbackAnalysisRepository.processFrame(
        buffer, width, height, timestampMs, frameIndex
    )

    suspend fun extractLabelsAtPosition(
        uri: String,
        positionMs: Long,
        glResult: ProcessedFrame,
    ): Map<Int, Boolean?> = playbackAnalysisRepository.extractLabelsAtPosition(
        uri, positionMs, glResult
    )

    fun getVideoDimensions(uri: String): Pair<Int, Int>? =
        playbackAnalysisRepository.getVideoDimensions(uri)

    fun clearProcessingThreadCache() =
        playbackAnalysisRepository.clearProcessingThreadCache()

    suspend fun closeMetadataSession() =
        playbackAnalysisRepository.closeMetadataSession()

    /** OWNER 확정 후 OTHER로 바꾸지 않음. 디코드에서는 OWNER만 반영. */
    fun mergeLabelsWithoutOverwritingOwner(
        current: Map<Int, Boolean?>,
        newLabels: Map<Int, Boolean?>,
    ): Map<Int, Boolean?> {
        val out = current.toMutableMap()
        for ((tid, newVal) in newLabels) {
            when (out[tid]) {
                true -> { }
                else -> if (newVal == true) out[tid] = true
            }
        }
        return out
    }
}
