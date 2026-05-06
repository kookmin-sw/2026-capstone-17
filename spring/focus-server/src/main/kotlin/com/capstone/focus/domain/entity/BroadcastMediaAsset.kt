package com.capstone.focus.domain.entity

import com.capstone.focus.domain.base.UlidPrimaryKeyEntity
import com.capstone.focus.domain.entity.enum.BroadcastMediaAssetType
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

@Entity
@Table(
    name = "broadcast_media_asset",
    indexes = [
        Index(name = "idx_broadcast_media_asset_broadcast_id", columnList = "broadcast_id")
    ]
)
@AttributeOverride(name = "id", column = Column(name = "media_asset_id"))
class BroadcastMediaAsset(
    broadcast: Broadcast,
    assetType: BroadcastMediaAssetType,
    storageProvider: String,
    storageKey: String,
    storageUrl: String? = null,
    durationSec: Long? = null,
    resolutionWidth: Int? = null,
    resolutionHeight: Int? = null,
    fileSizeBytes: Long? = null
) : UlidPrimaryKeyEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broadcast_id", nullable = false)
    var broadcast: Broadcast = broadcast
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 30)
    var assetType: BroadcastMediaAssetType = assetType
        protected set

    @Column(name = "storage_provider", nullable = false, length = 20)
    var storageProvider: String = storageProvider
        protected set

    @Column(name = "storage_key", nullable = false, length = 500)
    var storageKey: String = storageKey
        protected set

    @Column(name = "storage_url", length = 1000)
    var storageUrl: String? = storageUrl
        protected set

    @Column(name = "duration_sec")
    var durationSec: Long? = durationSec
        protected set

    @Column(name = "resolution_width")
    var resolutionWidth: Int? = resolutionWidth
        protected set

    @Column(name = "resolution_height")
    var resolutionHeight: Int? = resolutionHeight
        protected set

    @Column(name = "file_size_bytes")
    var fileSizeBytes: Long? = fileSizeBytes
        protected set

    fun updateAnalysisMetadata(
        storageUrl: String?,
        durationSec: Long?,
        resolutionWidth: Int?,
        resolutionHeight: Int?,
        fileSizeBytes: Long?
    ) {
        this.storageUrl = storageUrl ?: this.storageUrl
        this.durationSec = durationSec ?: this.durationSec
        this.resolutionWidth = resolutionWidth ?: this.resolutionWidth
        this.resolutionHeight = resolutionHeight ?: this.resolutionHeight
        this.fileSizeBytes = fileSizeBytes ?: this.fileSizeBytes
    }
}
