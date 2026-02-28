package com.kmu_focus.focusandroid.feature.video.data.repository

import android.media.MediaMetadataRetriever
import com.kmu_focus.focusandroid.core.media.data.local.VideoLocalDataSource
import com.kmu_focus.focusandroid.feature.video.data.transcoder.VideoTranscoder
import com.kmu_focus.focusandroid.feature.video.domain.repository.VideoRepository
import com.kmu_focus.focusandroid.feature.video.domain.usecase.TranscodeProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
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

    override suspend fun saveRecordingToGallery(
        recordingFilePath: String,
    ): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val recordingFile = File(recordingFilePath)
            require(recordingFile.exists()) { "녹화 파일이 존재하지 않습니다: $recordingFilePath" }
            waitForRecordingFileReady(recordingFile)
            localDataSource.moveToGallery(recordingFile)
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

    private suspend fun waitForRecordingFileReady(file: File) {
        var previousLength = -1L

        repeat(RECORDING_READY_CHECK_RETRY_COUNT) {
            if (!file.exists()) {
                delay(RECORDING_READY_CHECK_INTERVAL_MS)
                return@repeat
            }

            val length = file.length()
            val durationMs = readDurationMs(file)
            val hasContent = length >= MIN_VALID_MP4_BYTES && durationMs > 0L
            val isStableLength = previousLength > 0L && length == previousLength
            if (hasContent && isStableLength) return

            previousLength = length
            delay(RECORDING_READY_CHECK_INTERVAL_MS)
        }

        val finalDurationMs = if (file.exists()) readDurationMs(file) else 0L
        require(finalDurationMs > 0L) {
            "녹화 파일이 아직 완료되지 않았거나 0초 영상입니다."
        }
    }

    private fun readDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private companion object {
        private const val RECORDING_READY_CHECK_INTERVAL_MS = 120L
        private const val RECORDING_READY_CHECK_RETRY_COUNT = 50
        private const val MIN_VALID_MP4_BYTES = 4 * 1024L
    }
}
