package com.capstone.focus.domain.entity

import com.capstone.focus.domain.base.UlidPrimaryKeyEntity
import com.capstone.focus.domain.entity.enum.BroadcastAiReportType
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
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(
    name = "broadcast_ai_report",
    indexes = [
        Index(name = "idx_broadcast_ai_report_broadcast_id", columnList = "broadcast_id")
    ]
)
@AttributeOverride(name = "id", column = Column(name = "ai_report_id"))
class BroadcastAiReport(
    broadcast: Broadcast,
    analysisJob: BroadcastAnalysisJob,
    reportType: BroadcastAiReportType,
    title: String,
    summary: String,
    strengths: List<String> = emptyList(),
    weaknesses: List<String> = emptyList(),
    actionItems: List<String> = emptyList(),
    peakViewerCount: Long? = null,
    peakViewerOccurredAt: LocalDateTime? = null,
    peakSceneDescription: String? = null,
    totalReplacedFaceCount: Long? = null,
    maxSimultaneousCrowdCount: Int? = null,
    contentRatios: List<BroadcastContentRatio> = emptyList()
) : UlidPrimaryKeyEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broadcast_id", nullable = false)
    var broadcast: Broadcast = broadcast
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_job_id", nullable = false)
    var analysisJob: BroadcastAnalysisJob = analysisJob
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 30)
    var reportType: BroadcastAiReportType = reportType
        protected set

    @Column(name = "title", nullable = false, length = 255)
    var title: String = title
        protected set

    @Column(name = "summary", nullable = false, length = 5000)
    var summary: String = summary
        protected set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "strengths_json", columnDefinition = "jsonb", nullable = false)
    var strengths: List<String> = strengths
        protected set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "weaknesses_json", columnDefinition = "jsonb", nullable = false)
    var weaknesses: List<String> = weaknesses
        protected set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_items_json", columnDefinition = "jsonb", nullable = false)
    var actionItems: List<String> = actionItems
        protected set

    @Column(name = "peak_viewer_count")
    var peakViewerCount: Long? = peakViewerCount
        protected set

    @Column(name = "peak_viewer_occurred_at")
    var peakViewerOccurredAt: LocalDateTime? = peakViewerOccurredAt
        protected set

    @Column(name = "peak_scene_description", length = 2000)
    var peakSceneDescription: String? = peakSceneDescription
        protected set

    @Column(name = "total_replaced_face_count")
    var totalReplacedFaceCount: Long? = totalReplacedFaceCount
        protected set

    @Column(name = "max_simultaneous_crowd_count")
    var maxSimultaneousCrowdCount: Int? = maxSimultaneousCrowdCount
        protected set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_ratios_json", columnDefinition = "jsonb", nullable = false)
    var contentRatios: List<BroadcastContentRatio> = contentRatios
        protected set

    fun updateReport(
        title: String,
        summary: String,
        strengths: List<String>,
        weaknesses: List<String>,
        actionItems: List<String>,
        peakViewerCount: Long?,
        peakViewerOccurredAt: LocalDateTime?,
        peakSceneDescription: String?,
        totalReplacedFaceCount: Long?,
        maxSimultaneousCrowdCount: Int?,
        contentRatios: List<BroadcastContentRatio>
    ) {
        this.title = title
        this.summary = summary
        this.strengths = strengths
        this.weaknesses = weaknesses
        this.actionItems = actionItems
        this.peakViewerCount = peakViewerCount
        this.peakViewerOccurredAt = peakViewerOccurredAt
        this.peakSceneDescription = peakSceneDescription
        this.totalReplacedFaceCount = totalReplacedFaceCount
        this.maxSimultaneousCrowdCount = maxSimultaneousCrowdCount
        this.contentRatios = contentRatios
    }
}
