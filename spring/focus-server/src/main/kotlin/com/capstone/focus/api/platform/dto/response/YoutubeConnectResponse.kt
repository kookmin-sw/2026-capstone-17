package com.capstone.focus.api.platform.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "유튜브 연동 URL 응답")
data class YoutubeConnectResponse(
    @Schema(description = "유튜브 OAuth 연동 URL", requiredMode = Schema.RequiredMode.REQUIRED)
    val authUrl: String
)
