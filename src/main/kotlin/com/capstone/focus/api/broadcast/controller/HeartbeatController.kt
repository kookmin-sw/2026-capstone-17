package com.capstone.focus.api.broadcast.controller

import com.capstone.focus.api.broadcast.service.HeartbeatService
import com.capstone.focus.auth.security.service.FocusMemberDetails
import com.capstone.focus.common.common.annotations.FocusPostMapping
import com.capstone.focus.common.common.dto.ApiResponse
import com.capstone.focus.domain.base.util.ResponseUtil
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/broadcasts")
@Tag(name = "Heartbeat API", description = "실시간 방송 생존 신고 API")
@SecurityRequirement(name = "bearerAuth")
class HeartbeatController(
    private val heartbeatService: HeartbeatService
) {

    @FocusPostMapping("/{broadcastId}/heartbeat")
    @SwaggerApiResponse(responseCode = "200", description = "시청자 생존 신고 성공")
    @Operation(summary = "시청자 생존 신고 API", description = "10초마다 시청자의 생존(연결) 상태를 갱신합니다.")
    fun viewerHeartbeat(
        @AuthenticationPrincipal details: FocusMemberDetails,
        @PathVariable broadcastId: String
    ): ResponseEntity<ApiResponse.Success<Unit>> {
        heartbeatService.viewerHeartbeat(broadcastId, details.getMemberId())
        return ResponseUtil.success("시청자 하트비트가 갱신되었습니다.")
    }

    @FocusPostMapping("/{broadcastId}/streamer-heartbeat")
    @SwaggerApiResponse(responseCode = "200", description = "방장 생존 신고 성공")
    @Operation(summary = "방장 생존 신고 API", description = "10초마다 방장의 생존(연결) 상태를 갱신합니다.")
    fun streamerHeartbeat(
        @AuthenticationPrincipal details: FocusMemberDetails,
        @PathVariable broadcastId: String
    ): ResponseEntity<ApiResponse.Success<Unit>> {
        heartbeatService.streamerHeartbeat(broadcastId, details.getMemberId())
        return ResponseUtil.success("방장 하트비트가 갱신되었습니다.")
    }
}