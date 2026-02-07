package com.kmu_focus.focusandroid.feature.video.data.processor

import android.graphics.Bitmap
import com.kmu_focus.focusandroid.feature.detection.domain.detector.FaceDetector
import com.kmu_focus.focusandroid.feature.video.domain.entity.ProcessedFrame
import javax.inject.Inject

class FrameProcessor @Inject constructor(
    private val faceDetector: FaceDetector
) {
    fun process(bitmap: Bitmap, timestampMs: Long): ProcessedFrame {
        val faces = faceDetector.detectFaces(bitmap)
            .filter { it.confidence >= CONFIDENCE_THRESHOLD }
        return ProcessedFrame(
            faces = faces,
            frameWidth = bitmap.width,
            frameHeight = bitmap.height,
            timestampMs = timestampMs
        )
    }

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.5f
    }
}
