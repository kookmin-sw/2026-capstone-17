package com.capstone.focus.domain.repository

import com.capstone.focus.domain.entity.BroadcastAiReport
import org.springframework.data.jpa.repository.JpaRepository

interface BroadcastAiReportRepository : JpaRepository<BroadcastAiReport, String> {
    fun findTopByBroadcastIdOrderByCreatedAtDesc(broadcastId: String): BroadcastAiReport?
    fun findByAnalysisJobId(analysisJobId: String): BroadcastAiReport?
}
