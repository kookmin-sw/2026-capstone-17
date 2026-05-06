package com.capstone.focus.api.broadcast.dto.response

import com.capstone.focus.domain.entity.Broadcast
import com.capstone.focus.domain.entity.enum.BroadcastOutputMode
import com.capstone.focus.domain.entity.enum.BroadcastStatus
import com.capstone.focus.domain.entity.enum.StreamingPlatform
import java.time.LocalDateTime

data class BroadcastResponse(
    val broadcastId: String,
    val title: String?,
    val memberName: String,
    val memberId: String,
    val status: BroadcastStatus,
    val liveStatus: BroadcastStatus,
    val platform: StreamingPlatform,
    val outputMode: BroadcastOutputMode,
    val streamKey: String,
    val platformChannelId: String?,
    val watchUrl: String?,
    val hlsUrl: String?,
    val lastStartFailureReason: String?,
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
                liveStatus = broadcast.status,
                platform = broadcast.platform,
                outputMode = broadcast.outputMode,
                streamKey = broadcast.streamKey,
                platformChannelId = broadcast.platformChannelId,
                watchUrl = broadcast.watchUrl,
                hlsUrl = broadcast.hlsUrl,
                lastStartFailureReason = broadcast.lastStartFailureReason,
                startedAt = broadcast.startedAt,
                endedAt = broadcast.endedAt
            )
        }
    }
}
