package com.kmu_focus.focusandroid.core.media.data.local

import java.io.File

interface VideoLocalDataSource {
    suspend fun copyVideoToInternalStorage(sourceUri: String): String
    suspend fun saveVideoToGallery(sourceUri: String): String
    fun createTempOutputFile(): File
    fun deleteFile(file: File): Boolean
    suspend fun moveToGallery(file: File): String
}
