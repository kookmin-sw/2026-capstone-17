package com.capstone.focus.domain.repository

import com.capstone.focus.domain.entity.BroadcastHighlightCandidate
import org.springframework.data.jpa.repository.JpaRepository

interface BroadcastHighlightCandidateRepository : JpaRepository<BroadcastHighlightCandidate, String> {
    fun findAllByAnalysisJobIdOrderByScoreDescStartSecAsc(analysisJobId: String): List<BroadcastHighlightCandidate>
}
