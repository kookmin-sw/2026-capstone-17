package com.kmu_focus.focusandroid.feature.ai.domain.detector

import android.graphics.Bitmap
import android.graphics.Rect
import com.kmu_focus.focusandroid.feature.ai.domain.entity.Face3DMMResult

interface Facial3DMMExtractor {
    fun extract3DMM(frame: Bitmap, faceRect: Rect): Face3DMMResult?
    fun release()
}
