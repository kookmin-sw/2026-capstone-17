package com.kmu_focus.focusandroid.feature.video.domain.repository

interface VideoRepository {
    suspend fun saveVideo(sourceUri: String): Result<String>
    suspend fun saveVideoToGallery(sourceUri: String): Result<String>
}
