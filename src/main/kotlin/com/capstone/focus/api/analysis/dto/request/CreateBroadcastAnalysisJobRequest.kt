package com.capstone.focus.api.analysis.dto.request

import com.capstone.focus.domain.entity.enum.BroadcastAnalysisJobType
import com.capstone.focus.domain.entity.enum.BroadcastMediaAssetType
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero

@Schema(description = "방송 분석 작업 생성 요청")
data class CreateBroadcastAnalysisJobRequest(
    @Schema(description = "분석 대상 미디어 자산 유형", example = "ANALYSIS_MP4", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    val assetType: BroadcastMediaAssetType = BroadcastMediaAssetType.ANALYSIS_MP4,

    @Schema(description = "분석 작업 유형", example = "FULL_SUMMARY", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    val jobType: BroadcastAnalysisJobType = BroadcastAnalysisJobType.FULL_SUMMARY,

    @Schema(description = "스토리지 제공자", example = "S3", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    val storageProvider: String = "S3",

    @field:NotBlank(message = "storageKey는 필수입니다.")
    @Schema(description = "스토리지 키", example = "broadcasts/01ABC/archive/analysis.mp4", requiredMode = Schema.RequiredMode.REQUIRED)
    val storageKey: String,

    @Schema(description = "스토리지 URL", example = "https://bucket.s3.ap-northeast-2.amazonaws.com/broadcasts/01ABC/archive/analysis.mp4", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    val storageUrl: String? = null,

    @field:PositiveOrZero(message = "durationSec는 0 이상이어야 합니다.")
    @Schema(description = "영상 길이(초)", example = "3600", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    val durationSec: Long? = null,

    @field:Positive(message = "resolutionWidth는 1 이상이어야 합니다.")
    @Schema(description = "가로 해상도", example = "854", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    val resolutionWidth: Int? = null,

    @field:Positive(message = "resolutionHeight는 1 이상이어야 합니다.")
    @Schema(description = "세로 해상도", example = "480", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    val resolutionHeight: Int? = null,

    @field:PositiveOrZero(message = "fileSizeBytes는 0 이상이어야 합니다.")
    @Schema(description = "파일 크기(Bytes)", example = "157286400", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
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
