package com.capstone.focus.api.analysis.dto.response

import com.capstone.focus.domain.entity.BroadcastHighlightCandidate
import java.time.LocalDateTime

data class BroadcastHighlightCandidateResponse(
    val highlightCandidateId: String,
    val startSec: Long,
    val endSec: Long,
    val title: String,
    val reason: String,
    val score: Double,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(candidate: BroadcastHighlightCandidate): BroadcastHighlightCandidateResponse {
            return BroadcastHighlightCandidateResponse(
                highlightCandidateId = candidate.id,
                startSec = candidate.startSec,
                endSec = candidate.endSec,
                title = candidate.title,
                reason = candidate.reason,
                score = candidate.score,
                createdAt = candidate.createdAt
            )
        }
    }
}
