package com.capstone.focus.auth.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "사용자 정보 응답")
data class UserInfoResponse(
    @Schema(description = "사용자 ID", example = "01HZX...", nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
    val id: String,
    @Schema(description = "카카오 ID", example = "123456789", nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
    val kakaoId: Long,
    @Schema(description = "이름", example = "홍길동", nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
    val name: String,
    @Schema(description = "이메일", example = "user@example.com", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    val email: String?,
    @Schema(description = "프로필 이미지 URL", example = "https://k.kakaocdn.net/profile.jpg", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    val profileImageUrl: String?
)
