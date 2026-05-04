package com.capstone.focus.api.analysis.dto.response

import com.capstone.focus.domain.entity.BroadcastMediaAsset
import com.capstone.focus.domain.entity.enum.BroadcastMediaAssetType
import java.time.LocalDateTime

data class BroadcastMediaAssetResponse(
    val mediaAssetId: String,
    val assetType: BroadcastMediaAssetType,
    val storageProvider: String,
    val storageKey: String,
    val storageUrl: String?,
    val durationSec: Long?,
    val resolutionWidth: Int?,
    val resolutionHeight: Int?,
    val fileSizeBytes: Long?,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(asset: BroadcastMediaAsset): BroadcastMediaAssetResponse {
            return BroadcastMediaAssetResponse(
                mediaAssetId = asset.id,
                assetType = asset.assetType,
                storageProvider = asset.storageProvider,
                storageKey = asset.storageKey,
                storageUrl = asset.storageUrl,
                durationSec = asset.durationSec,
                resolutionWidth = asset.resolutionWidth,
                resolutionHeight = asset.resolutionHeight,
                fileSizeBytes = asset.fileSizeBytes,
                createdAt = asset.createdAt
            )
        }
    }
}
