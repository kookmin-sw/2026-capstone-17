package com.kmu_focus.focusandroid.feature.video.domain.usecase

import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.OwnerAdder
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import com.kmu_focus.focusandroid.feature.video.domain.repository.PlaybackAnalysisRepository
import javax.inject.Inject

class RegisterOwnerDuringPlaybackUseCase @Inject constructor(
    private val playbackAnalysisRepository: PlaybackAnalysisRepository,
    private val ownerAdder: OwnerAdder,
) {
    suspend fun registerOwnerAndGetOwnerId(
        uri: String,
        positionMs: Long,
        glResult: ProcessedFrame,
        trackId: Int,
    ): Int? {
        val embedding = playbackAnalysisRepository.extractEmbeddingForTrack(
            uri = uri,
            positionMs = positionMs,
            glResult = glResult,
            trackId = trackId,
        ) ?: return null

        return ownerAdder.addOwnerFromEmbeddingWithOwnerId(embedding)
    }

    suspend fun replaceOwnerEmbedding(
        uri: String,
        positionMs: Long,
        glResult: ProcessedFrame,
        trackId: Int,
        ownerId: Int,
    ): Boolean {
        val embedding = playbackAnalysisRepository.extractEmbeddingForTrack(
            uri = uri,
            positionMs = positionMs,
            glResult = glResult,
            trackId = trackId,
        ) ?: return false

        return ownerAdder.replaceOwnerEmbedding(ownerId, embedding)
    }

    suspend fun saveFaceSnapshotTemporarily(
        uri: String,
        positionMs: Long,
        glResult: ProcessedFrame,
        trackId: Int,
    ): String? {
        return playbackAnalysisRepository.saveFaceSnapshotForTrack(
            uri = uri,
            positionMs = positionMs,
            glResult = glResult,
            trackId = trackId,
        )
    }

    fun deleteTemporaryFaceSnapshot(snapshotUri: String): Boolean {
        return playbackAnalysisRepository.deleteTemporaryFaceSnapshot(snapshotUri)
    }

    suspend operator fun invoke(
        uri: String,
        positionMs: Long,
        glResult: ProcessedFrame,
        trackId: Int,
    ): Boolean {
        return registerOwnerAndGetOwnerId(uri, positionMs, glResult, trackId) != null
    }
}
