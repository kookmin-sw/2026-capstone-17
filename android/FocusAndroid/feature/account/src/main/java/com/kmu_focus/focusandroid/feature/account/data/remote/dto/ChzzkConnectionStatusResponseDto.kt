package com.kmu_focus.focusandroid.feature.account.data.remote.dto

import com.kmu_focus.focusandroid.feature.account.domain.entity.ChzzkConnectionStatus

data class ChzzkConnectionStatusResponseDto(
    val connected: Boolean,
    val channelId: String? = null,
    val channelName: String? = null,
    val watchUrl: String? = null,
    val accessTokenExpiresAt: String? = null,
    val connectedAt: String? = null,
)

fun ChzzkConnectionStatusResponseDto.toEntity(): ChzzkConnectionStatus {
    return ChzzkConnectionStatus(
        connected = connected,
        channelId = channelId,
        channelName = channelName,
        watchUrl = watchUrl,
        accessTokenExpiresAt = accessTokenExpiresAt,
        connectedAt = connectedAt,
    )
}
