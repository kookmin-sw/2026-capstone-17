package com.capstone.focus.auth.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "토큰 응답")
data class TokenResponse(
    @Schema(description = "액세스 토큰", example = "eyJhbGci...", nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
    val accessToken: String,
    @Schema(description = "리프레시 토큰", example = "eyJhbGci...", nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
    val refreshToken: String
)