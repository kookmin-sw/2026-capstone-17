package com.capstone.focus.api.analysis.dto.request

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero

@Schema(description = "방송 분석 작업 완료 요청")
data class CompleteBroadcastAnalysisJobRequest(
    @Schema(description = "분석 완료 후 저장된 미디어 URL", example = "https://bucket.s3.ap-northeast-2.amazonaws.com/broadcasts/01ABC/archive/analysis.mp4")
    val storageUrl: String? = null,

    @field:PositiveOrZero(message = "durationSec는 0 이상이어야 합니다.")
    @Schema(description = "영상 길이(초)", example = "3600")
    val durationSec: Long? = null,

    @field:Positive(message = "resolutionWidth는 1 이상이어야 합니다.")
    @Schema(description = "가로 해상도", example = "854")
    val resolutionWidth: Int? = null,

    @field:Positive(message = "resolutionHeight는 1 이상이어야 합니다.")
    @Schema(description = "세로 해상도", example = "480")
    val resolutionHeight: Int? = null,

    @field:PositiveOrZero(message = "fileSizeBytes는 0 이상이어야 합니다.")
    @Schema(description = "파일 크기(Bytes)", example = "157286400")
    val fileSizeBytes: Long? = null,

    @Schema(description = "AI가 생성한 방송 요약 문구")
    val summary: String? = null,

    @field:JsonSetter(nulls = Nulls.AS_EMPTY)
    @Schema(description = "리포트 강점 목록")
    val strengths: List<String> = emptyList(),

    @field:JsonSetter(nulls = Nulls.AS_EMPTY)
    @Schema(description = "리포트 보완점 목록")
    val weaknesses: List<String> = emptyList(),

    @field:JsonSetter(nulls = Nulls.AS_EMPTY)
    @Schema(description = "다음 방송 액션 아이템")
    val actionItems: List<String> = emptyList(),

    @field:Valid
    @Schema(description = "시청자 최고점 정보")
    val viewerPeakInsight: ViewerPeakInsightRequest? = null,

    @field:Valid
    @Schema(description = "타인 얼굴 및 군중 통계")
    val faceStatistics: FaceStatisticsRequest? = null,

    @field:JsonSetter(nulls = Nulls.AS_EMPTY)
    @field:Valid
    @Schema(description = "방송 콘텐츠 비율 목록")
    val contentRatios: List<ContentRatioRequest> = emptyList()
)
