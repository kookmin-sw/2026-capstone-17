package com.kmu_focus.focusandroid.data.local

interface VideoLocalDataSource {
    suspend fun copyVideoToInternalStorage(sourceUri: String): String
}
