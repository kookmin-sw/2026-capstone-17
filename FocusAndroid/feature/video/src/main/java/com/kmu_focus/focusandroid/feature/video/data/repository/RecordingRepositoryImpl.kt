package com.kmu_focus.focusandroid.feature.video.data.repository

import android.util.Log
import com.kmu_focus.focusandroid.core.media.data.local.VideoLocalDataSource
import com.kmu_focus.focusandroid.core.media.data.recorder.AudioTrackExtractor
import com.kmu_focus.focusandroid.core.media.data.recorder.RealTimeRecorder
import com.kmu_focus.focusandroid.feature.video.domain.repository.RecordingRepository
import java.io.File
import javax.inject.Inject

class RecordingRepositoryImpl @Inject constructor(
    private val realTimeRecorder: RealTimeRecorder,
    private val videoLocalDataSource: VideoLocalDataSource,
    private val audioExtractorFactory: AudioTrackExtractor.Factory,
) : RecordingRepository {

    override fun startRecording(
        width: Int,
        height: Int,
        onSurfaceReady: (encoderSurface: Any, width: Int, height: Int) -> Unit,
        sourceUri: String?,
        audioStartPositionMs: Long,
    ): File {
        val file = videoLocalDataSource.createTempOutputFile()
        val audioTrackSource = sourceUri
            ?.takeIf { it.isNotBlank() }
            ?.let { uri ->
                runCatching { audioExtractorFactory.create(uri) }
                    .onFailure { error -> Log.w(TAG, "오디오 추출기 생성 실패: $uri", error) }
                    .getOrNull()
            }

        try {
            realTimeRecorder.start(
                width = width,
                height = height,
                outputFile = file,
                audioTrackSource = audioTrackSource,
                audioStartPositionUs = audioStartPositionMs.coerceAtLeast(0L) * MILLIS_TO_MICROS,
                onInputSurfaceReady = { surface ->
                    onSurfaceReady(surface, width, height)
                },
            )
        } catch (error: Exception) {
            runCatching { audioTrackSource?.release() }
            throw error
        }
        return file
    }

    override fun stopRecording() {
        try {
            realTimeRecorder.stop()
        } catch (e: Exception) {
            Log.e(TAG, "녹화 중지 실패", e)
        }
    }

    override val lastRecordingSampleCount: Int
        get() = realTimeRecorder.lastRecordingSampleCount

    private companion object {
        private const val TAG = "RecordingRepository"
        private const val MILLIS_TO_MICROS = 1_000L
    }
}
