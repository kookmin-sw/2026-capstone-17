package com.capstone.focus.api.broadcast.dto.request

import io.swagger.v3.oas.annotations.media.Schema

data class UpdateBroadcastRequest(
    @Schema(description = "수정할 방송 제목", example = "제목 수정함")
    val title: String?
)