package com.capstone.focus.domain.entity

import com.capstone.focus.domain.base.UlidPrimaryKeyEntity
import com.capstone.focus.domain.entity.enum.BroadcastAnalysisJobStatus
import com.capstone.focus.domain.entity.enum.BroadcastAnalysisJobType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(
    name = "broadcast_analysis_job",
    indexes = [
        Index(name = "idx_broadcast_analysis_job_broadcast_id", columnList = "broadcast_id"),
        Index(name = "idx_broadcast_analysis_job_status", columnList = "job_status")
    ]
)
@AttributeOverride(name = "id", column = Column(name = "analysis_job_id"))
class BroadcastAnalysisJob(
    broadcast: Broadcast,
    mediaAsset: BroadcastMediaAsset,
    jobType: BroadcastAnalysisJobType,
    jobStatus: BroadcastAnalysisJobStatus = BroadcastAnalysisJobStatus.PENDING
) : UlidPrimaryKeyEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broadcast_id", nullable = false)
    var broadcast: Broadcast = broadcast
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_asset_id", nullable = false)
    var mediaAsset: BroadcastMediaAsset = mediaAsset
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 30)
    var jobType: BroadcastAnalysisJobType = jobType
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "job_status", nullable = false, length = 20)
    var jobStatus: BroadcastAnalysisJobStatus = jobStatus
        protected set

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null
        protected set

    @Column(name = "error_message", length = 2000)
    var errorMessage: String? = null
        protected set

    fun markRunning() {
        jobStatus = BroadcastAnalysisJobStatus.RUNNING
        errorMessage = null
    }

    fun markSucceeded() {
        jobStatus = BroadcastAnalysisJobStatus.SUCCEEDED
        completedAt = LocalDateTime.now()
        errorMessage = null
    }

    fun markFailed(errorMessage: String) {
        jobStatus = BroadcastAnalysisJobStatus.FAILED
        completedAt = LocalDateTime.now()
        this.errorMessage = errorMessage
    }
}
