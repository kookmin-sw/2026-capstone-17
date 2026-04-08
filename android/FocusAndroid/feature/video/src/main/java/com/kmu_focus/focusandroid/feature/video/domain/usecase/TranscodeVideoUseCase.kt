package com.kmu_focus.focusandroid.feature.video.domain.usecase

import com.kmu_focus.focusandroid.feature.video.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

sealed interface TranscodeProgress {
    data class InProgress(val progress: Float) : TranscodeProgress
    data class Complete(val outputPath: String) : TranscodeProgress
    data class Error(val message: String) : TranscodeProgress
}

class TranscodeVideoUseCase @Inject constructor(
    private val videoRepository: VideoRepository
) {
    operator fun invoke(sourceUri: String): Flow<TranscodeProgress> {
        return videoRepository.transcodeAndSaveToGallery(sourceUri)
    }
}
