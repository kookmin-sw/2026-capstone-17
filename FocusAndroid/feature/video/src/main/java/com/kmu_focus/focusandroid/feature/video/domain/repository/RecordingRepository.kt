package com.kmu_focus.focusandroid.feature.video.domain.repository

import java.io.File

/**
 * 실시간 녹화 담당. 인코더 Surface는 onSurfaceReady로 전달.
 * encoderSurface: Any는 android.view.Surface (domain에서 Android 의존성 제거).
 */
interface RecordingRepository {
    /** @return 출력 파일 (임시 생성). 실패 시 예외. */
    fun startRecording(
        width: Int,
        height: Int,
        onSurfaceReady: (encoderSurface: Any, width: Int, height: Int) -> Unit,
    ): File

    fun stopRecording()

    val lastRecordingSampleCount: Int
}
