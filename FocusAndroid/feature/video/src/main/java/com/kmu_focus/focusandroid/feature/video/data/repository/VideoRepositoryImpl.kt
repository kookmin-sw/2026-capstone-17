package com.kmu_focus.focusandroid.feature.video.data.repository

import com.kmu_focus.focusandroid.feature.video.data.local.VideoLocalDataSource
import com.kmu_focus.focusandroid.feature.video.data.transcoder.VideoTranscoder
import com.kmu_focus.focusandroid.feature.video.domain.repository.VideoRepository
import com.kmu_focus.focusandroid.feature.video.domain.usecase.TranscodeProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class VideoRepositoryImpl @Inject constructor(
    private val localDataSource: VideoLocalDataSource,
    private val videoTranscoder: VideoTranscoder
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

    override fun transcodeAndSaveToGallery(sourceUri: String): Flow<TranscodeProgress> = flow {
        val tempFile = localDataSource.createTempOutputFile()
        try {
            var lastProgress: TranscodeProgress? = null
            videoTranscoder.transcode(sourceUri, tempFile).collect { progress ->
                when (progress) {
                    is TranscodeProgress.Complete -> {
                        val galleryUri = localDataSource.moveToGallery(tempFile)
                        emit(TranscodeProgress.Complete(galleryUri))
                    }
                    is TranscodeProgress.Error -> {
                        tempFile.delete()
                        emit(progress)
                    }
                    is TranscodeProgress.InProgress -> {
                        emit(progress)
                    }
                }
                lastProgress = progress
            }
        } catch (e: Exception) {
            tempFile.delete()
            emit(TranscodeProgress.Error(e.message ?: "트랜스코딩 실패"))
        }
    }
}
