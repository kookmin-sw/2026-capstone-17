package com.capstone.focus.api.broadcast.dto.request

import io.swagger.v3.oas.annotations.media.Schema

data class CreateBroadcastRequest (
    @Schema(description = "방송 제목", example = "오늘의 코딩 방송")
    val title: String?
)