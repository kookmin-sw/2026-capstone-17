package com.capstone.focus.api.broadcast.controller

import com.capstone.focus.api.broadcast.dto.request.CreateBroadcastRequest
import com.capstone.focus.api.broadcast.dto.request.StartBroadcastRequest
import com.capstone.focus.api.broadcast.dto.request.UpdateBroadcastRequest
import com.capstone.focus.api.broadcast.dto.response.BroadcastResponse
import com.capstone.focus.api.broadcast.service.BroadcastService
import com.capstone.focus.auth.security.service.FocusMemberDetails
import com.capstone.focus.common.common.annotations.FocusDeleteMapping
import com.capstone.focus.common.common.annotations.FocusGetMapping
import com.capstone.focus.common.common.annotations.FocusPostMapping
import com.capstone.focus.common.common.annotations.FocusPutMapping
import com.capstone.focus.common.common.dto.ApiResponse
import com.capstone.focus.domain.base.util.ResponseUtil
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@RestController
@RequestMapping("/api/v1/broadcasts")
@Tag(name = "Broadcast API", description = "방송 생성/관리 API")
@SecurityRequirement(name = "bearerAuth")
class BroadcastController(
    private val broadcastService: BroadcastService
) {

    @FocusPostMapping(authenticated = true)
    @SwaggerApiResponse(responseCode = "201", description = "방송 생성 성공")
    @Operation(summary = "방송 생성 API", description = "새로운 방송(방)을 생성하고 스트림 키를 발급받습니다.")
    fun createBroadcast(
        @AuthenticationPrincipal details: FocusMemberDetails,
        @RequestBody @Valid request: CreateBroadcastRequest
    ): ResponseEntity<ApiResponse.Success<BroadcastResponse>> {
        val response = broadcastService.createBroadcast(details.getMemberId(), request)
        return ResponseUtil.success("방송이 성공적으로 생성되었습니다.", response)
    }

    @FocusPostMapping("/{broadcastId}/start")
    @SwaggerApiResponse(responseCode = "200", description = "방송 시작 성공")
    @Operation(summary = "방송 시작 API", description = "FastAPI 워커를 시작하고 설정된 출력 모드에 따라 CHZZK RTMP 또는 HLS fallback으로 방송 상태를 ON_AIR로 변경합니다.")
    fun startBroadcast(
        @AuthenticationPrincipal details: FocusMemberDetails,
        @PathVariable broadcastId: String,
        @RequestBody @Valid request: StartBroadcastRequest
    ): ResponseEntity<ApiResponse.Success<BroadcastResponse>> {

        val response = broadcastService.startBroadcast(details.getMemberId(), broadcastId, request)
        return ResponseUtil.success("방송이 성공적으로 시작되었습니다.", response)
    }

    @FocusPostMapping("/{broadcastId}/stop")
    @SwaggerApiResponse(responseCode = "200", description = "방송 종료 성공")
    @Operation(summary = "방송 종료 API", description = "방송 상태를 ENDED로 변경합니다.")
    fun stopBroadcast(
        @AuthenticationPrincipal details: FocusMemberDetails,
        @PathVariable broadcastId: String
    ): ResponseEntity<ApiResponse.Success<BroadcastResponse>> {
        val response = broadcastService.stopBroadcast(details.getMemberId(), broadcastId)
        return ResponseUtil.success("방송이 성공적으로 종료되었습니다.", response)
    }

    @FocusGetMapping
    @SwaggerApiResponse(responseCode = "200", description = "방송 리스트 조회 성공")
    @Operation(summary = "방송 리스트 조회 API (페이징)", description = "생성된 방송 목록을 페이징하여 조회합니다. (기본: 최신순)")
    fun getBroadcastList(
        @PageableDefault(size = 10, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable
    ): ResponseEntity<ApiResponse.Success<Page<BroadcastResponse>>> {
        val broadcasts = broadcastService.getBroadcastList(pageable)
        return ResponseUtil.success("방송 리스트를 성공적으로 조회했습니다.", broadcasts)
    }

    @FocusGetMapping("/{broadcastId}")
    @SwaggerApiResponse(responseCode = "200", description = "방송 상세 조회 성공")
    @Operation(summary = "방송 상세 조회 API", description = "특정 방송의 상세 정보를 조회합니다.")
    fun getBroadcastDetail(
        @PathVariable broadcastId: String
    ): ResponseEntity<ApiResponse.Success<BroadcastResponse>> {
        val response = broadcastService.getBroadcastDetail(broadcastId)
        return ResponseUtil.success("방송 상세 정보를 성공적으로 조회했습니다.", response)
    }

    @FocusPutMapping("/{broadcastId}", authenticated = true)
    @SwaggerApiResponse(responseCode = "200", description = "방송 정보 수정 성공")
    @Operation(summary = "방송 정보 수정 API", description = "방송 제목 등 정보를 수정합니다. (본인만 가능)")
    fun updateBroadcast(
        @AuthenticationPrincipal details: FocusMemberDetails,
        @PathVariable broadcastId: String,
        @RequestBody @Valid request: UpdateBroadcastRequest
    ): ResponseEntity<ApiResponse.Success<BroadcastResponse>> {
        val response = broadcastService.updateBroadcast(details.getMemberId(), broadcastId, request)
        return ResponseUtil.success("방송 정보가 성공적으로 수정되었습니다.", response)
    }

    @FocusDeleteMapping("/{broadcastId}", authenticated = true)
    @SwaggerApiResponse(responseCode = "200", description = "방송 삭제 성공")
    @Operation(summary = "방송 삭제 API", description = "방송을 삭제합니다. (본인만 가능)")
    fun deleteBroadcast(
        @AuthenticationPrincipal details: FocusMemberDetails,
        @PathVariable broadcastId: String
    ): ResponseEntity<ApiResponse.Success<Unit>> {
        broadcastService.deleteBroadcast(details.getMemberId(), broadcastId)
        return ResponseUtil.success("방송이 성공적으로 삭제되었습니다.")
    }
}
