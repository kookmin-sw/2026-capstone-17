package com.kmu_focus.focusandroid.feature.ai.domain.detector

import android.graphics.Bitmap
import com.kmu_focus.focusandroid.feature.ai.domain.entity.DetectedFace

interface FaceDetector {
    fun detectFaces(frame: Bitmap): List<DetectedFace>
    fun release()
    fun getDetectorType(): String
}
