package com.kmu_focus.focusandroid.feature.video.domain.entity

import com.kmu_focus.focusandroid.feature.ai.domain.entity.DetectedFace

data class ProcessedFrame(
    val faces: List<DetectedFace>,
    val frameWidth: Int,
    val frameHeight: Int,
    val timestampMs: Long,
    val frameExport: FrameExport? = null
)
