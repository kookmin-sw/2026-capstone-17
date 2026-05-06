package com.capstone.focus.auth.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "카카오 로그인 요청")
data class KakaoLoginRequest(
    @Schema(
        description = "카카오 SDK에서 발급받은 access token",
        example = "r8gXxwAAAA...",
        requiredMode = Schema.RequiredMode.REQUIRED,
        nullable = false
    )
    @field:NotBlank(message = "카카오 access token은 필수입니다.")
    val accessToken: String
)
