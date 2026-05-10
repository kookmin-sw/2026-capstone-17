package com.kmu_focus.focusandroid.feature.camera.domain.usecase

import com.kmu_focus.focusandroid.feature.camera.domain.repository.CameraRecordingRepository
import java.io.File
import javax.inject.Inject

class CameraRecordingUseCase @Inject constructor(
    private val cameraRecordingRepository: CameraRecordingRepository,
) {
    fun startRecording(
        width: Int,
        height: Int,
        onSurfaceReady: (Any, Int, Int) -> Unit,
    ): Result<File> = runCatching {
        cameraRecordingRepository.startRecording(
            width = width,
            height = height,
            onSurfaceReady = onSurfaceReady,
        )
    }

    fun startBroadcastRecording(
        width: Int,
        height: Int,
        muxerFactory: Any,
        onSurfaceReady: (Any, Int, Int) -> Unit,
    ): Result<Unit> = runCatching {
        cameraRecordingRepository.startBroadcastRecording(
            width = width,
            height = height,
            muxerFactory = muxerFactory,
            onSurfaceReady = onSurfaceReady,
        )
    }

    fun stopRecording(): Result<Unit> = runCatching {
        cameraRecordingRepository.stopRecording()
    }
}
