package com.capstone.focus.auth.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "토큰 재발급 요청")
data class RefreshTokenRequest(
    @Schema(description = "리프레시 토큰", example = "eyJhbGci...", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
    @field:NotBlank(message = "리프레시 토큰은 필수입니다")
    val refreshToken: String
)