package com.kmu_focus.focusandroid.data.repository

import com.kmu_focus.focusandroid.data.local.VideoLocalDataSource
import com.kmu_focus.focusandroid.domain.repository.VideoRepository
import javax.inject.Inject

class VideoRepositoryImpl @Inject constructor(
    private val localDataSource: VideoLocalDataSource
) : VideoRepository {

    override suspend fun saveVideo(sourceUri: String): Result<String> {
        return runCatching {
            localDataSource.copyVideoToInternalStorage(sourceUri)
        }
    }
}
