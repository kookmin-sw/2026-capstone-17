package com.capstone.focus.api.analysis.controller

import com.capstone.focus.api.analysis.dto.request.CompleteBroadcastAnalysisJobRequest
import com.capstone.focus.api.analysis.dto.request.CreateBroadcastAnalysisJobRequest
import com.capstone.focus.api.analysis.dto.response.BroadcastAnalysisJobResponse
import com.capstone.focus.api.analysis.dto.response.BroadcastAnalysisResultResponse
import com.capstone.focus.api.analysis.dto.response.BroadcastHighlightCandidateResponse
import com.capstone.focus.api.analysis.service.BroadcastAnalysisService
import com.capstone.focus.auth.security.service.FocusMemberDetails
import com.capstone.focus.common.common.annotations.FocusGetMapping
import com.capstone.focus.common.common.annotations.FocusPostMapping
import com.capstone.focus.common.common.dto.ApiResponse
import com.capstone.focus.domain.base.util.ResponseUtil
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/broadcasts")
@Tag(name = "방송 분석 API", description = "방송 종료 후 AI 분석 작업 생성 및 결과 조회 API")
@SecurityRequirement(name = "bearerAuth")
class BroadcastAnalysisController(
    private val broadcastAnalysisService: BroadcastAnalysisService
) {

    @FocusPostMapping("/{broadcastId}/analysis-jobs", authenticated = true)
    @SwaggerApiResponse(responseCode = "200", description = "방송 분석 작업 생성 성공")
    @Operation(summary = "방송 분석 작업 생성", description = "분석 대상 영상 메타데이터와 초기 리포트 입력을 저장합니다.")
    fun createAnalysisJob(
        @AuthenticationPrincipal details: FocusMemberDetails,
        @PathVariable broadcastId: String,
        @RequestBody @Valid request: CreateBroadcastAnalysisJobRequest
    ): ResponseEntity<ApiResponse.Success<BroadcastAnalysisJobResponse>> {
        val response = broadcastAnalysisService.createAnalysisJob(details.getMemberId(), broadcastId, request)
        return ResponseUtil.success("방송 분석 작업 생성 성공", response)
    }

    @FocusPostMapping("/{broadcastId}/analysis-jobs/{analysisJobId}/complete", authenticated = true)
    @SwaggerApiResponse(responseCode = "200", description = "방송 분석 작업 완료 처리 성공")
    @Operation(summary = "방송 분석 작업 완료 처리", description = "자동 생성된 분석 작업에 최종 AI/집계 결과를 반영합니다.")
    fun completeAnalysisJob(
        @AuthenticationPrincipal details: FocusMemberDetails,
        @PathVariable broadcastId: String,
        @PathVariable analysisJobId: String,
        @RequestBody @Valid request: CompleteBroadcastAnalysisJobRequest
    ): ResponseEntity<ApiResponse.Success<BroadcastAnalysisJobResponse>> {
        val response = broadcastAnalysisService.completeAnalysisJob(details.getMemberId(), broadcastId, analysisJobId, request)
        return ResponseUtil.success("방송 분석 작업 완료 처리 성공", response)
    }

    @FocusGetMapping("/{broadcastId}/analysis", authenticated = true)
    @SwaggerApiResponse(responseCode = "200", description = "최신 방송 분석 결과 조회 성공")
    @Operation(summary = "최신 방송 분석 결과 조회", description = "최신 분석 작업과 리포트 요약 정보를 조회합니다.")
    fun getLatestAnalysis(
        @AuthenticationPrincipal details: FocusMemberDetails,
        @PathVariable broadcastId: String
    ): ResponseEntity<ApiResponse.Success<BroadcastAnalysisResultResponse>> {
        val response = broadcastAnalysisService.getLatestAnalysis(details.getMemberId(), broadcastId)
        return ResponseUtil.success("최신 방송 분석 결과 조회 성공", response)
    }

    @FocusGetMapping("/{broadcastId}/highlights", authenticated = true)
    @SwaggerApiResponse(responseCode = "200", description = "방송 하이라이트 후보 조회 성공")
    @Operation(summary = "방송 하이라이트 후보 조회", description = "최신 분석 작업 기준 하이라이트 후보 구간을 조회합니다.")
    fun getHighlights(
        @AuthenticationPrincipal details: FocusMemberDetails,
        @PathVariable broadcastId: String
    ): ResponseEntity<ApiResponse.Success<List<BroadcastHighlightCandidateResponse>>> {
        val response = broadcastAnalysisService.getHighlights(details.getMemberId(), broadcastId)
        return ResponseUtil.success("방송 하이라이트 후보 조회 성공", response)
    }
}
