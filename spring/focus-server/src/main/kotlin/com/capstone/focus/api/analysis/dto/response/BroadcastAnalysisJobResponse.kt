package com.capstone.focus.api.analysis.dto.response

import com.capstone.focus.domain.entity.BroadcastAnalysisJob
import com.capstone.focus.domain.entity.enum.BroadcastAnalysisJobStatus
import com.capstone.focus.domain.entity.enum.BroadcastAnalysisJobType
import java.time.LocalDateTime

data class BroadcastAnalysisJobResponse(
    val analysisJobId: String,
    val broadcastId: String,
    val jobType: BroadcastAnalysisJobType,
    val jobStatus: BroadcastAnalysisJobStatus,
    val completedAt: LocalDateTime?,
    val errorMessage: String?,
    val createdAt: LocalDateTime,
    val mediaAsset: BroadcastMediaAssetResponse
) {
    companion object {
        fun from(job: BroadcastAnalysisJob): BroadcastAnalysisJobResponse {
            return BroadcastAnalysisJobResponse(
                analysisJobId = job.id,
                broadcastId = job.broadcast.id,
                jobType = job.jobType,
                jobStatus = job.jobStatus,
                completedAt = job.completedAt,
                errorMessage = job.errorMessage,
                createdAt = job.createdAt,
                mediaAsset = BroadcastMediaAssetResponse.from(job.mediaAsset)
            )
        }
    }
}
