package com.capstone.focus.api.analysis.controller

import com.capstone.focus.api.analysis.dto.request.CompleteBroadcastAnalysisJobRequest
import com.capstone.focus.api.analysis.dto.response.BroadcastAnalysisJobResponse
import com.capstone.focus.api.analysis.service.BroadcastAnalysisService
import com.capstone.focus.common.common.dto.ApiResponse
import com.capstone.focus.common.config.InternalApiProperties
import com.capstone.focus.common.exception.ApiException
import com.capstone.focus.common.exception.ErrorTitle
import com.capstone.focus.domain.base.util.ResponseUtil
import io.swagger.v3.oas.annotations.Hidden
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Hidden
@RestController
@RequestMapping("/internal/broadcasts")
class InternalBroadcastAnalysisController(
    private val broadcastAnalysisService: BroadcastAnalysisService,
    private val internalApiProperties: InternalApiProperties
) {

    @GetMapping("/{broadcastId}/analysis-jobs/latest")
    fun getLatestFullSummaryJob(
        @RequestHeader("X-Internal-Api-Key") internalApiKey: String,
        @PathVariable broadcastId: String
    ): ResponseEntity<ApiResponse.Success<BroadcastAnalysisJobResponse?>> {
        validateInternalApiKey(internalApiKey)
        val response = broadcastAnalysisService.getLatestFullSummaryJob(broadcastId)
        return ResponseUtil.success("최신 분석 작업 조회 성공", response)
    }

    @PostMapping("/{broadcastId}/analysis-jobs/{analysisJobId}/complete")
    fun completeAnalysisJob(
        @RequestHeader("X-Internal-Api-Key") internalApiKey: String,
        @PathVariable broadcastId: String,
        @PathVariable analysisJobId: String,
        @RequestBody @Valid request: CompleteBroadcastAnalysisJobRequest
    ): ResponseEntity<ApiResponse.Success<BroadcastAnalysisJobResponse>> {
        validateInternalApiKey(internalApiKey)
        val response = broadcastAnalysisService.completeAnalysisJobInternal(broadcastId, analysisJobId, request)
        return ResponseUtil.success("내부 분석 작업 완료 처리 성공", response)
    }

    private fun validateInternalApiKey(internalApiKey: String) {
        if (internalApiProperties.key.isBlank() || internalApiProperties.key != internalApiKey) {
            throw ApiException(ErrorTitle.Forbidden)
        }
    }
}
