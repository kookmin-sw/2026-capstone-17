package com.kmu_focus.focusandroid.feature.video.domain.repository

import com.kmu_focus.focusandroid.feature.video.domain.entity.ProcessedFrame
import java.nio.ByteBuffer

interface PlaybackAnalysisRepository {
    fun processFrame(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        timestampMs: Long,
        frameIndex: Int?,
    ): ProcessedFrame

    suspend fun extractLabelsAtPosition(
        uri: String,
        positionMs: Long,
        glResult: ProcessedFrame,
    ): Map<Int, Boolean?>

    fun getVideoDimensions(uri: String): Pair<Int, Int>?
}
