package com.capstone.focus.api.analysis.dto.request

import com.capstone.focus.domain.entity.BroadcastContentRatio
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero

@Schema(description = "방송 콘텐츠 비율 항목")
data class ContentRatioRequest(
    @field:NotBlank(message = "contentType은 필수입니다.")
    @Schema(description = "콘텐츠 유형", example = "이동")
    val contentType: String,

    @field:PositiveOrZero(message = "percentage는 0 이상이어야 합니다.")
    @Schema(description = "콘텐츠 비율", example = "45.0")
    val percentage: Double,

    @field:PositiveOrZero(message = "durationSec는 0 이상이어야 합니다.")
    @Schema(description = "해당 콘텐츠 구간 길이(초)", example = "6480")
    val durationSec: Long
) {
    fun toDomain(): BroadcastContentRatio {
        return BroadcastContentRatio(
            contentType = contentType,
            percentage = percentage,
            durationSec = durationSec
        )
    }
}
