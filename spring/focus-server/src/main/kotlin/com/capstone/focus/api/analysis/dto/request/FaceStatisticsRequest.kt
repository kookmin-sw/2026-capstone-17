package com.capstone.focus.api.analysis.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.PositiveOrZero

@Schema(description = "타인 얼굴 및 군중 통계")
data class FaceStatisticsRequest(
    @field:PositiveOrZero(message = "totalReplacedFaceCount는 0 이상이어야 합니다.")
    @Schema(description = "총 타인 얼굴 아바타 치환 수", example = "342")
    val totalReplacedFaceCount: Long? = null,

    @field:PositiveOrZero(message = "maxSimultaneousCrowdCount는 0 이상이어야 합니다.")
    @Schema(description = "최대 동시 군중 수", example = "12")
    val maxSimultaneousCrowdCount: Int? = null
)
