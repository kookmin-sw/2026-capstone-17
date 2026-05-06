package com.capstone.focus.domain.entity

import com.capstone.focus.domain.base.UlidPrimaryKeyEntity
import com.capstone.focus.domain.entity.enum.StreamingPlatform
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
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "streaming_platform_connection",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_streaming_platform_connection_member_platform",
            columnNames = ["member_id", "platform"]
        )
    ],
    indexes = [
        Index(name = "idx_streaming_platform_connection_member_id", columnList = "member_id"),
        Index(name = "idx_streaming_platform_connection_platform_channel_id", columnList = "platform_channel_id")
    ]
)
@AttributeOverride(name = "id", column = Column(name = "connection_id"))
class StreamingPlatformConnection(
    member: Member,
    platform: StreamingPlatform,
    platformUserId: String,
    platformChannelId: String,
    platformChannelName: String?,
    accessToken: String,
    refreshToken: String,
    accessTokenExpiresAt: LocalDateTime
) : UlidPrimaryKeyEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    var member: Member = member
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    var platform: StreamingPlatform = platform
        protected set

    @Column(name = "platform_user_id", nullable = false, length = 100)
    var platformUserId: String = platformUserId
        protected set

    @Column(name = "platform_channel_id", nullable = false, length = 100)
    var platformChannelId: String = platformChannelId
        protected set

    @Column(name = "platform_channel_name", length = 100)
    var platformChannelName: String? = platformChannelName
        protected set

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    var accessToken: String = accessToken
        protected set

    @Column(name = "refresh_token", nullable = false, columnDefinition = "TEXT")
    var refreshToken: String = refreshToken
        protected set

    @Column(name = "access_token_expires_at", nullable = false)
    var accessTokenExpiresAt: LocalDateTime = accessTokenExpiresAt
        protected set

    @Column(name = "connected_at", nullable = false)
    var connectedAt: LocalDateTime = LocalDateTime.now()
        protected set

    @Column(name = "revoked_at")
    var revokedAt: LocalDateTime? = null
        protected set

    fun reconnect(
        platformUserId: String,
        platformChannelId: String,
        platformChannelName: String?,
        accessToken: String,
        refreshToken: String,
        accessTokenExpiresAt: LocalDateTime
    ) {
        this.platformUserId = platformUserId
        this.platformChannelId = platformChannelId
        this.platformChannelName = platformChannelName
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        this.accessTokenExpiresAt = accessTokenExpiresAt
        this.connectedAt = LocalDateTime.now()
        this.revokedAt = null
    }

    fun updateTokens(
        accessToken: String,
        refreshToken: String,
        accessTokenExpiresAt: LocalDateTime
    ) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        this.accessTokenExpiresAt = accessTokenExpiresAt
        this.revokedAt = null
    }

    fun revoke() {
        this.revokedAt = LocalDateTime.now()
    }

    fun isRevoked(): Boolean = revokedAt != null

    fun needsRefresh(bufferSeconds: Long): Boolean {
        return accessTokenExpiresAt.isBefore(LocalDateTime.now().plusSeconds(bufferSeconds))
    }
}
