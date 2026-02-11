package com.capstone.focus.auth.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "카카오 로그인 요청")
data class KakaoLoginRequest(
    @Schema(description = "카카오 인증 코드", example = "abc123def456", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
    @field:NotBlank(message = "인증 코드는 필수입니다")
    val code: String
)