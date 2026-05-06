package com.capstone.focus.api.platform.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "치지직 연동 상태 응답")
data class ChzzkConnectionStatusResponse(
    @Schema(description = "연동 여부", requiredMode = Schema.RequiredMode.REQUIRED)
    val connected: Boolean,
    @Schema(description = "치지직 채널 ID")
    val channelId: String? = null,
    @Schema(description = "치지직 채널명")
    val channelName: String? = null,
    @Schema(description = "시청 URL")
    val watchUrl: String? = null,
    @Schema(description = "Access Token 만료 시각")
    val accessTokenExpiresAt: LocalDateTime? = null,
    @Schema(description = "최초/최근 연동 시각")
    val connectedAt: LocalDateTime? = null
)
