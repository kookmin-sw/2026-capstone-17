package com.kmu_focus.focusandroid.feature.video.domain.entity

import com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace

data class ProcessedFrame(
    val faces: List<DetectedFace>,
    val frameWidth: Int,
    val frameHeight: Int,
    val timestampMs: Long,
    val frameExport: FrameExport? = null,
    /** faces와 동일 순서의 tracking ID (화면/연동용) */
    val trackingIds: List<Int> = emptyList()
)
