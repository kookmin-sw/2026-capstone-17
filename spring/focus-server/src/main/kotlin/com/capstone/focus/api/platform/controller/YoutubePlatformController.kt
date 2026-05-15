package com.capstone.focus.api.platform.controller

import com.capstone.focus.api.platform.dto.response.YoutubeConnectResponse
import com.capstone.focus.api.platform.dto.response.YoutubeConnectionStatusResponse
import com.capstone.focus.api.platform.service.YoutubePlatformService
import com.capstone.focus.auth.security.service.FocusMemberDetails
import com.capstone.focus.common.common.annotations.FocusDeleteMapping
import com.capstone.focus.common.common.annotations.FocusGetMapping
import com.capstone.focus.common.common.dto.ApiResponse
import com.capstone.focus.domain.base.util.ResponseUtil
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@RestController
@RequestMapping("/api/v1/platforms/youtube")
@Tag(name = "YouTube Platform API", description = "유튜브 채널 연동 및 상태 조회 API")
class YoutubePlatformController(
    private val youtubePlatformService: YoutubePlatformService
) {

    @FocusGetMapping("/connect", authenticated = true)
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerApiResponse(responseCode = "200", description = "유튜브 연동 URL 조회 성공")
    @Operation(summary = "유튜브 연동 시작 URL 조회", description = "로그인한 회원 기준으로 유튜브 OAuth 연동 URL을 생성합니다.")
    fun connect(
        @AuthenticationPrincipal details: FocusMemberDetails
    ): ResponseEntity<ApiResponse.Success<YoutubeConnectResponse>> {
        val response = youtubePlatformService.createConnectUrl(details.getMemberId())
        return ResponseUtil.success("유튜브 연동 URL을 생성했습니다.", response)
    }

    @FocusGetMapping("/callback")
    @SwaggerApiResponse(responseCode = "200", description = "유튜브 연동 성공")
    @Operation(summary = "유튜브 OAuth callback", description = "유튜브가 인가 코드를 반환하면 토큰 교환 후 회원과 채널 연동을 저장합니다.")
    fun callback(
        @RequestParam code: String,
        @RequestParam state: String
    ): ResponseEntity<ApiResponse.Success<YoutubeConnectionStatusResponse>> {
        val response = youtubePlatformService.handleCallback(code = code, state = state)
        return ResponseUtil.success("유튜브 채널 연동이 완료되었습니다.", response)
    }

    @FocusGetMapping("/status", authenticated = true)
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerApiResponse(responseCode = "200", description = "유튜브 연동 상태 조회 성공")
    @Operation(summary = "유튜브 연동 상태 조회", description = "현재 로그인한 회원의 유튜브 채널 연동 상태를 조회합니다.")
    fun status(
        @AuthenticationPrincipal details: FocusMemberDetails
    ): ResponseEntity<ApiResponse.Success<YoutubeConnectionStatusResponse>> {
        val response = youtubePlatformService.getConnectionStatus(details.getMemberId())
        return ResponseUtil.success("유튜브 연동 상태를 조회했습니다.", response)
    }

    @FocusDeleteMapping("/connection", authenticated = true)
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerApiResponse(responseCode = "200", description = "유튜브 연동 해제 성공")
    @Operation(summary = "유튜브 연동 해제", description = "현재 로그인한 회원의 유튜브 토큰을 revoke하고 연동을 해제합니다.")
    fun disconnect(
        @AuthenticationPrincipal details: FocusMemberDetails
    ): ResponseEntity<ApiResponse.Success<Unit>> {
        youtubePlatformService.disconnect(details.getMemberId())
        return ResponseUtil.success("유튜브 연동이 해제되었습니다.")
    }
}
