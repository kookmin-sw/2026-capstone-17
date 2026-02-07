package com.kmu_focus.focusandroid.feature.detection.domain.detector

import android.graphics.Bitmap
import android.graphics.Rect
import com.kmu_focus.focusandroid.feature.detection.domain.entity.Face3DMMResult

interface FacialLandmarkDetector {
    fun detectLandmarks(frame: Bitmap, faceRect: Rect): Face3DMMResult?
    fun release()
}
