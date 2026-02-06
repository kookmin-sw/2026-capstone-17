package com.kmu_focus.focusandroid.feature.video.data.local

interface VideoLocalDataSource {
    suspend fun copyVideoToInternalStorage(sourceUri: String): String
}
