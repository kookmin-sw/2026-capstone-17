package com.kmu_focus.focusandroid.core.ai.domain.entity

data class DetectedFace(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val confidence: Float,
    val landmarks: FaceLandmarks5? = null
)
