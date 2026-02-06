package com.kmu_focus.focusandroid.domain.repository

interface VideoRepository {
    suspend fun saveVideo(sourceUri: String): Result<String>
}
