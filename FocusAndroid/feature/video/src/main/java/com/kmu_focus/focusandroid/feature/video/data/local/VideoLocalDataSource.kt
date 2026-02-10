package com.kmu_focus.focusandroid.feature.video.data.local

import java.io.File

interface VideoLocalDataSource {
    suspend fun copyVideoToInternalStorage(sourceUri: String): String
    suspend fun saveVideoToGallery(sourceUri: String): String
    fun createTempOutputFile(): File
    suspend fun moveToGallery(file: File): String
}
