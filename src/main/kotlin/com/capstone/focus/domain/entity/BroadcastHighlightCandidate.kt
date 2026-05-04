package com.capstone.focus.domain.entity

import com.capstone.focus.domain.base.UlidPrimaryKeyEntity
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(
    name = "broadcast_highlight_candidate",
    indexes = [
        Index(name = "idx_broadcast_highlight_candidate_broadcast_id", columnList = "broadcast_id"),
        Index(name = "idx_broadcast_highlight_candidate_analysis_job_id", columnList = "analysis_job_id")
    ]
)
@AttributeOverride(name = "id", column = Column(name = "highlight_candidate_id"))
class BroadcastHighlightCandidate(
    broadcast: Broadcast,
    analysisJob: BroadcastAnalysisJob,
    startSec: Long,
    endSec: Long,
    title: String,
    reason: String,
    score: Double
) : UlidPrimaryKeyEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broadcast_id", nullable = false)
    var broadcast: Broadcast = broadcast
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_job_id", nullable = false)
    var analysisJob: BroadcastAnalysisJob = analysisJob
        protected set

    @Column(name = "start_sec", nullable = false)
    var startSec: Long = startSec
        protected set

    @Column(name = "end_sec", nullable = false)
    var endSec: Long = endSec
        protected set

    @Column(name = "title", nullable = false, length = 255)
    var title: String = title
        protected set

    @Column(name = "reason", nullable = false, length = 2000)
    var reason: String = reason
        protected set

    @Column(name = "score", nullable = false)
    var score: Double = score
        protected set
}
