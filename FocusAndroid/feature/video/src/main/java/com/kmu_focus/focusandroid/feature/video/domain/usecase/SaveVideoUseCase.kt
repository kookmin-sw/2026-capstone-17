package com.kmu_focus.focusandroid.feature.video.domain.usecase

import com.kmu_focus.focusandroid.feature.video.domain.repository.VideoRepository
import javax.inject.Inject

class SaveVideoUseCase @Inject constructor(
    private val videoRepository: VideoRepository
) {
    suspend operator fun invoke(sourceUri: String): Result<String> {
        return videoRepository.saveVideo(sourceUri)
    }

    suspend fun invokeToGallery(sourceUri: String): Result<String> {
        return videoRepository.saveVideoToGallery(sourceUri)
    }

    suspend fun invokeRecordingWithSourceAudioToGallery(
        recordingFilePath: String,
        sourceUri: String
    ): Result<String> {
        return videoRepository.saveRecordingWithSourceAudioToGallery(recordingFilePath, sourceUri)
    }
}
