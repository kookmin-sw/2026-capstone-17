package com.capstone.focus.domain.entity

import com.capstone.focus.domain.base.UlidPrimaryKeyEntity
import com.capstone.focus.domain.entity.enum.BroadcastStatus
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
import java.net.URL
import java.time.LocalDateTime

@Entity
@Table(
    name = "broadcast",
    indexes = [
        Index(name = "idx_broadcast_member_id", columnList = "member_id"),
        Index(name = "idx_broadcast_stream_key", columnList = "stream_key")
    ])
@AttributeOverride(name = "id", column = Column(name = "broadcast_id"))
class Broadcast(
    member: Member,
    streamKey: String,
    title: String? = null
) : UlidPrimaryKeyEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    var member: Member = member
        protected set

    @Column(name = "stream_key", nullable = false, unique = true)
    var streamKey: String = streamKey
        protected set

    @Column(name = "title")
    var title: String? = title
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    var status: BroadcastStatus = BroadcastStatus.READY
        protected set

    @Column(name = "hls_url", length = 255)
    var hlsUrl: String? = null
        protected set

    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null
        protected set

    @Column(name = "ended_at")
    var endedAt: LocalDateTime? = null
        protected set

    fun startBroadcast(hlsUrl: String) {
        this.status = BroadcastStatus.ON_AIR
        this.startedAt = LocalDateTime.now()
        this.hlsUrl = hlsUrl
    }

    fun endBroadcast() {
        this.status = BroadcastStatus.ENDED
        this.endedAt = LocalDateTime.now()
    }
}