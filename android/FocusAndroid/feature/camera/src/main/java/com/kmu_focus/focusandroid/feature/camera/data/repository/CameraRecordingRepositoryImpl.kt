package com.kmu_focus.focusandroid.feature.camera.data.repository

import com.kmu_focus.focusandroid.feature.camera.data.audio.MicAudioSource
import com.kmu_focus.focusandroid.feature.camera.domain.repository.CameraRecordingRepository
import com.kmu_focus.focusandroid.core.media.data.local.VideoLocalDataSource
import com.kmu_focus.focusandroid.core.media.data.recorder.RealTimeRecorder
import java.io.File
import javax.inject.Inject
import javax.inject.Provider

class CameraRecordingRepositoryImpl @Inject constructor(
    private val realTimeRecorder: RealTimeRecorder,
    private val videoLocalDataSource: VideoLocalDataSource,
    private val micAudioSourceProvider: Provider<MicAudioSource>,
) : CameraRecordingRepository {

    override fun startRecording(
        width: Int,
        height: Int,
        onSurfaceReady: (Any, Int, Int) -> Unit,
    ): File {
        val outputFile = videoLocalDataSource.createTempOutputFile()
        val micAudioSource = micAudioSourceProvider.get()

        try {
            realTimeRecorder.start(
                width = width,
                height = height,
                outputFile = outputFile,
                audioTrackSource = micAudioSource,
                onInputSurfaceReady = { surface ->
                    onSurfaceReady(surface, width, height)
                },
            )
        } catch (error: Exception) {
            runCatching { micAudioSource.release() }
            throw error
        }

        return outputFile
    }

    override fun stopRecording() {
        realTimeRecorder.stop()
    }
}
