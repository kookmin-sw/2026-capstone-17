package com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto

data class BroadcastResponseDto(
    val broadcastId: String,
    val title: String,
    val status: String,
    val streamKey: String,
    val hlsUrl: String?,
    val memberName: String,
    val memberId: String,
    val startedAt: String?,
    val endedAt: String?,
)
