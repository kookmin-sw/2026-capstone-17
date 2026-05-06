package com.capstone.focus.domain.repository

import com.capstone.focus.domain.entity.BroadcastAnalysisJob
import org.springframework.data.jpa.repository.JpaRepository

interface BroadcastAnalysisJobRepository : JpaRepository<BroadcastAnalysisJob, String> {
    fun findTopByBroadcastIdOrderByCreatedAtDesc(broadcastId: String): BroadcastAnalysisJob?
    fun findTopByBroadcastIdAndJobTypeOrderByCreatedAtDesc(
        broadcastId: String,
        jobType: com.capstone.focus.domain.entity.enum.BroadcastAnalysisJobType
    ): BroadcastAnalysisJob?
}
