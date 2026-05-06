package com.capstone.focus.api.analysis.dto.response

import com.capstone.focus.domain.entity.BroadcastAiReport
import com.capstone.focus.domain.entity.BroadcastContentRatio
import com.capstone.focus.domain.entity.enum.BroadcastAiReportType
import java.time.LocalDateTime

data class ViewerPeakInsightResponse(
    val peakViewerCount: Long,
    val occurredAt: LocalDateTime?,
    val sceneDescription: String?
)

data class FaceStatisticsResponse(
    val totalReplacedFaceCount: Long?,
    val maxSimultaneousCrowdCount: Int?
)

data class ContentRatioResponse(
    val contentType: String,
    val percentage: Double,
    val durationSec: Long
) {
    companion object {
        fun from(contentRatio: BroadcastContentRatio): ContentRatioResponse {
            return ContentRatioResponse(
                contentType = contentRatio.contentType,
                percentage = contentRatio.percentage,
                durationSec = contentRatio.durationSec
            )
        }
    }
}

data class BroadcastAiReportResponse(
    val aiReportId: String,
    val reportType: BroadcastAiReportType,
    val title: String,
    val summary: String,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val actionItems: List<String>,
    val viewerPeakInsight: ViewerPeakInsightResponse?,
    val faceStatistics: FaceStatisticsResponse,
    val contentRatios: List<ContentRatioResponse>,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(report: BroadcastAiReport): BroadcastAiReportResponse {
            return BroadcastAiReportResponse(
                aiReportId = report.id,
                reportType = report.reportType,
                title = report.title,
                summary = report.summary,
                strengths = report.strengths,
                weaknesses = report.weaknesses,
                actionItems = report.actionItems,
                viewerPeakInsight = report.peakViewerCount?.let {
                    ViewerPeakInsightResponse(
                        peakViewerCount = it,
                        occurredAt = report.peakViewerOccurredAt,
                        sceneDescription = report.peakSceneDescription
                    )
                },
                faceStatistics = FaceStatisticsResponse(
                    totalReplacedFaceCount = report.totalReplacedFaceCount,
                    maxSimultaneousCrowdCount = report.maxSimultaneousCrowdCount
                ),
                contentRatios = report.contentRatios.map { ContentRatioResponse.from(it) },
                createdAt = report.createdAt
            )
        }
    }
}
