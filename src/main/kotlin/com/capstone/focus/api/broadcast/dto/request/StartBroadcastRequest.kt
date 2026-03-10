package com.capstone.focus.api.broadcast.dto.request

import jakarta.validation.constraints.NotBlank

data class StartBroadcastRequest(
    @field:NotBlank(message = "HLS URL은 필수 입력 값입니다.")
    val hlsUrl: String
)
