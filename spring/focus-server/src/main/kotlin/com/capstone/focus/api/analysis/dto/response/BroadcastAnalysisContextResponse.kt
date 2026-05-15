package com.capstone.focus.api.analysis.dto.response

import com.capstone.focus.domain.entity.BroadcastContentRatio
import java.time.LocalDateTime

data class BroadcastAnalysisContextResponse(
    val broadcastId: String,
    val viewerPeakInsight: ViewerPeakInsightResponse?,
    val contentRatios: List<ContentRatioResponse>,
    val sampledSnapshotCount: Int,
    val lastSampledAt: LocalDateTime?
) {
    companion object {
        fun of(
            broadcastId: String,
            peakViewerCount: Long?,
            peakOccurredAt: LocalDateTime?,
            peakSceneDescription: String?,
            contentRatios: List<BroadcastContentRatio>,
            sampledSnapshotCount: Int,
            lastSampledAt: LocalDateTime?
        ): BroadcastAnalysisContextResponse {
            return BroadcastAnalysisContextResponse(
                broadcastId = broadcastId,
                viewerPeakInsight = peakViewerCount?.let {
                    ViewerPeakInsightResponse(
                        peakViewerCount = it,
                        occurredAt = peakOccurredAt,
                        sceneDescription = peakSceneDescription
                    )
                },
                contentRatios = contentRatios.map { ContentRatioResponse.from(it) },
                sampledSnapshotCount = sampledSnapshotCount,
                lastSampledAt = lastSampledAt
            )
        }
    }
}
