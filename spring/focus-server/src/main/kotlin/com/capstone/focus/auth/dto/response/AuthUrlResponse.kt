package com.capstone.focus.auth.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "인증 URL 응답")
data class AuthUrlResponse(
    @Schema(description = "카카오 로그인 URL", example = "https://kauth.kakao.com/oauth/authorize?client_id=...", nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
    val authUrl: String
)