package com.capstone.focus.api.broadcast.dto.response

import com.capstone.focus.domain.entity.Broadcast
import com.capstone.focus.domain.entity.enum.BroadcastStatus
import java.time.LocalDateTime

data class BroadcastResponse(
    val broadcastId: String,
    val title: String?,
    val memberName: String,
    val memberId: String,
    val status: BroadcastStatus,
    val streamKey: String,
    val hlsUrl: String?,
    val startedAt: LocalDateTime?,
    val endedAt: LocalDateTime?
) {
    companion object {
        fun from(broadcast: Broadcast): BroadcastResponse {
            return BroadcastResponse(
                broadcastId = broadcast.id,
                title = broadcast.title,
                memberName = broadcast.member.nickname ?: "Unknown",
                memberId = broadcast.member.id,
                status = broadcast.status,
                streamKey = broadcast.streamKey,
                hlsUrl = broadcast.hlsUrl,
                startedAt = broadcast.startedAt,
                endedAt = broadcast.endedAt
            )
        }
    }
}