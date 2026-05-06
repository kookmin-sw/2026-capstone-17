package com.capstone.focus.api.analysis.dto.response

data class BroadcastAnalysisResultResponse(
    val broadcastId: String,
    val latestJob: BroadcastAnalysisJobResponse?,
    val latestReport: BroadcastAiReportResponse?,
    val highlightCount: Int
)
