package com.kmu_focus.focusandroid.feature.video.data.repository

import android.util.Log
import com.kmu_focus.focusandroid.feature.video.data.local.VideoLocalDataSource
import com.kmu_focus.focusandroid.feature.video.data.recorder.RealTimeRecorder
import com.kmu_focus.focusandroid.feature.video.domain.repository.RecordingRepository
import java.io.File
import javax.inject.Inject

class RecordingRepositoryImpl @Inject constructor(
    private val realTimeRecorder: RealTimeRecorder,
    private val videoLocalDataSource: VideoLocalDataSource,
) : RecordingRepository {

    override fun startRecording(
        width: Int,
        height: Int,
        onSurfaceReady: (encoderSurface: Any, width: Int, height: Int) -> Unit,
    ): File {
        val file = videoLocalDataSource.createTempOutputFile()
        realTimeRecorder.start(
            width = width,
            height = height,
            outputFile = file,
            onInputSurfaceReady = { surface ->
                onSurfaceReady(surface, width, height)
            },
        )
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
    }
}
