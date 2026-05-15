package com.capstone.focus.api.broadcast.dto.request

import com.capstone.focus.domain.entity.enum.BroadcastOutputMode
import io.swagger.v3.oas.annotations.media.Schema

data class CreateBroadcastRequest (
    @Schema(description = "방송 제목", example = "오늘의 코딩 방송")
    val title: String?,
    @Schema(
        description = "송출 모드. 생략하면 서버 기본값을 사용합니다.",
        example = "CHZZK_RTMP",
        allowableValues = ["HLS", "CHZZK_RTMP", "YOUTUBE_RTMP"]
    )
    val outputMode: BroadcastOutputMode? = null
)
