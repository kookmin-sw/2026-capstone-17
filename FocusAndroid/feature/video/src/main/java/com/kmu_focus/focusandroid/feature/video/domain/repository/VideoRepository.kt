package com.kmu_focus.focusandroid.feature.video.domain.repository

import com.kmu_focus.focusandroid.feature.video.domain.usecase.TranscodeProgress
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    suspend fun saveVideo(sourceUri: String): Result<String>
    suspend fun saveVideoToGallery(sourceUri: String): Result<String>
    suspend fun saveRecordingWithSourceAudioToGallery(
        recordingFilePath: String,
        sourceUri: String
    ): Result<String>
    fun transcodeAndSaveToGallery(sourceUri: String): Flow<TranscodeProgress>
}
