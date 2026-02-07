package com.kmu_focus.focusandroid.feature.video.domain.entity

import com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace

data class ProcessedFrame(
    val faces: List<DetectedFace>,
    val frameWidth: Int,
    val frameHeight: Int,
    val timestampMs: Long,
    val frameExport: FrameExport? = null,
    val trackingIds: List<Int> = emptyList(),
    /** faces와 동일 순서. null=대기, true=OWNER, false=OTHER */
    val faceLabels: List<Boolean?> = emptyList()
)
