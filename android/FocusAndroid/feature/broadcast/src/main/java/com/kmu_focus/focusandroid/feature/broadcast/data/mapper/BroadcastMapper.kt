package com.kmu_focus.focusandroid.feature.broadcast.data.mapper

import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.BroadcastResponseDto
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastStatus

fun BroadcastResponseDto.toEntity(): Broadcast {
    return Broadcast(
        broadcastId = broadcastId,
        title = title,
        status = runCatching { BroadcastStatus.valueOf(status) }
            .getOrDefault(BroadcastStatus.ERROR),
        streamKey = streamKey,
        hlsUrl = hlsUrl,
        memberName = memberName,
        memberId = memberId,
        startedAt = startedAt,
        endedAt = endedAt,
    )
}
