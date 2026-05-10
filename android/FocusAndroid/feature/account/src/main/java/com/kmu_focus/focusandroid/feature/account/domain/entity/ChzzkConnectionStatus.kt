package com.kmu_focus.focusandroid.feature.account.domain.entity

data class ChzzkConnectionStatus(
    val connected: Boolean,
    val channelId: String? = null,
    val channelName: String? = null,
    val watchUrl: String? = null,
    val accessTokenExpiresAt: String? = null,
    val connectedAt: String? = null,
)
