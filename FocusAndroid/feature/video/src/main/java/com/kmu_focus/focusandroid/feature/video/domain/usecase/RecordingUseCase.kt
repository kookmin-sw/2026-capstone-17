package com.kmu_focus.focusandroid.feature.video.domain.usecase

import com.kmu_focus.focusandroid.feature.video.domain.repository.RecordingRepository
import java.io.File
import javax.inject.Inject

class RecordingUseCase @Inject constructor(
    private val recordingRepository: RecordingRepository,
) {
    fun startRecording(
        width: Int,
        height: Int,
        onSurfaceReady: (encoderSurface: Any, width: Int, height: Int) -> Unit,
    ): Result<File> = runCatching {
        recordingRepository.startRecording(width, height, onSurfaceReady)
    }

    fun stopRecording() {
        recordingRepository.stopRecording()
    }

    val lastRecordingSampleCount: Int
        get() = recordingRepository.lastRecordingSampleCount
}
