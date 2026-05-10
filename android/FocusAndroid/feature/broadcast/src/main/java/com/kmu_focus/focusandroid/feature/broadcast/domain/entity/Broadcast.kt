package com.kmu_focus.focusandroid.feature.broadcast.domain.entity

data class Broadcast(
    val broadcastId: String,
    val title: String,
    val status: BroadcastStatus,
    val streamKey: String,
    val hlsUrl: String?,
    val memberName: String,
    val memberId: String,
    val startedAt: String?,
    val endedAt: String?,
)
