package com.kmu_focus.focusandroid.feature.video.domain.repository

import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import java.nio.ByteBuffer

interface PlaybackAnalysisRepository {
    fun processFrame(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        timestampMs: Long,
        frameIndex: Int?,
    ): ProcessedFrame

    suspend fun extractLabelsAtPosition(
        uri: String,
        positionMs: Long,
        glResult: ProcessedFrame,
    ): Map<Int, Boolean?>

    suspend fun extractEmbeddingForTrack(
        uri: String,
        positionMs: Long,
        glResult: ProcessedFrame,
        trackId: Int,
    ): FloatArray?

    suspend fun saveFaceSnapshotForTrack(
        uri: String,
        positionMs: Long,
        glResult: ProcessedFrame,
        trackId: Int,
    ): String?

    fun deleteTemporaryFaceSnapshot(snapshotUri: String): Boolean

    fun getVideoDimensions(uri: String): Pair<Int, Int>?

    fun clearProcessingThreadCache() {}

    suspend fun closeMetadataSession() {}
}
