package com.capstone.focus.api.broadcast.dto.request

import io.swagger.v3.oas.annotations.media.Schema

data class StartBroadcastRequest(
    @Schema(description = "선택된 아바타 ID", example = "avatar-a")
    val avatarId: String? = null
)
