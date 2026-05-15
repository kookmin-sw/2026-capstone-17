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
import java.time.LocalDateTime

@Entity
@Table(
    name = "broadcast_platform_snapshot",
    indexes = [
        Index(name = "idx_broadcast_platform_snapshot_broadcast_id", columnList = "broadcast_id"),
        Index(name = "idx_broadcast_platform_snapshot_sampled_at", columnList = "sampled_at")
    ]
)
@AttributeOverride(name = "id", column = Column(name = "snapshot_id"))
class BroadcastPlatformSnapshot(
    broadcast: Broadcast,
    sampledAt: LocalDateTime,
    concurrentUserCount: Long? = null,
    categoryType: String? = null,
    categoryId: String? = null,
    categoryName: String? = null,
    liveTitle: String? = null
) : UlidPrimaryKeyEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broadcast_id", nullable = false)
    var broadcast: Broadcast = broadcast
        protected set

    @Column(name = "sampled_at", nullable = false)
    var sampledAt: LocalDateTime = sampledAt
        protected set

    @Column(name = "concurrent_user_count")
    var concurrentUserCount: Long? = concurrentUserCount
        protected set

    @Column(name = "category_type", length = 30)
    var categoryType: String? = categoryType
        protected set

    @Column(name = "category_id", length = 100)
    var categoryId: String? = categoryId
        protected set

    @Column(name = "category_name", length = 255)
    var categoryName: String? = categoryName
        protected set

    @Column(name = "live_title", length = 255)
    var liveTitle: String? = liveTitle
        protected set
}
