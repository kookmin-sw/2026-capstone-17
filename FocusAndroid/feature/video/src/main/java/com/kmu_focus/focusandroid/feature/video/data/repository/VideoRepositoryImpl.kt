package com.kmu_focus.focusandroid.feature.video.data.repository

import com.kmu_focus.focusandroid.feature.video.data.local.VideoLocalDataSource
import com.kmu_focus.focusandroid.feature.video.domain.repository.VideoRepository
import javax.inject.Inject

class VideoRepositoryImpl @Inject constructor(
    private val localDataSource: VideoLocalDataSource
) : VideoRepository {

    override suspend fun saveVideo(sourceUri: String): Result<String> {
        return runCatching {
            localDataSource.copyVideoToInternalStorage(sourceUri)
        }
    }

    override suspend fun saveVideoToGallery(sourceUri: String): Result<String> {
        return runCatching {
            localDataSource.saveVideoToGallery(sourceUri)
        }
    }
}
