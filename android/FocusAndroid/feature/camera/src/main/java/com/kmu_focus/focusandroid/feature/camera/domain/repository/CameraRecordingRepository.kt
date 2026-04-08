package com.kmu_focus.focusandroid.feature.camera.domain.repository

import java.io.File

interface CameraRecordingRepository {
    fun startRecording(
        width: Int,
        height: Int,
        onSurfaceReady: (encoderSurface: Any, width: Int, height: Int) -> Unit,
    ): File

    fun stopRecording()
}
