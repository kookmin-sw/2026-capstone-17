package com.capstone.focus.api.analysis.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.PositiveOrZero
import java.time.LocalDateTime

@Schema(description = "시청자 최고점 정보")
data class ViewerPeakInsightRequest(
    @field:PositiveOrZero(message = "peakViewerCount는 0 이상이어야 합니다.")
    @Schema(description = "최고 시청자 수", example = "500")
    val peakViewerCount: Long? = null,

    @Schema(description = "최고 시청자 수 기록 시각", example = "2026-04-23T14:15:00")
    val occurredAt: LocalDateTime? = null,

    @Schema(description = "최고점 당시 장면 설명", example = "탕후루 먹방을 진행하며 시청 반응이 가장 크게 올라간 시점입니다.")
    val sceneDescription: String? = null
)
