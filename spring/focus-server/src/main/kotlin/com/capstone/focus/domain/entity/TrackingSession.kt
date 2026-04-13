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
import org.springframework.data.annotation.CreatedDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "tracking_session",
    indexes = [
        Index(name = "idx_tracking_session_broadcast_id", columnList = "broadcast_id"),
        Index(name = "idx_tracking_session_avatar_id", columnList = "avatar_id"),
        Index(name = "idx_tracking_session_lookup", columnList = "broadcast_id, tracking_id")
    ])
@AttributeOverride(name = "id", column = Column(name = "tracking_session_id"))
class TrackingSession(
    broadcast: Broadcast,
    avatar: Avatar,
    trackingId: String
) : UlidPrimaryKeyEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broadcast_id", nullable = false)
    var broadcast: Broadcast = broadcast
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avatar_id", nullable = false)
    var avatar: Avatar = avatar
        protected set

    @Column(name = "tracking_id", nullable = false)
    var trackingId: String = trackingId
        protected set

    @CreatedDate
    @Column(name = "first_seen_at", nullable = false, updatable = false) // 생성 후 절대 변하지 않음
    var firstSeenAt: LocalDateTime = LocalDateTime.now()
        protected set

    // 트래킹 끝날 때만을 업데이트해야하니 자동화 x
    @Column(name = "last_seen_at")
    var lastSeenAt: LocalDateTime = LocalDateTime.now()
        protected set

    @Column(name = "is_active")
    var isActive: Boolean = true
        protected set

    fun updateLastSeen() {
        if (!this.isActive) return
        this.lastSeenAt = LocalDateTime.now()
    }

    fun deactivate() {
        this.isActive = false
        this.lastSeenAt = LocalDateTime.now()
    }
}