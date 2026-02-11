package com.capstone.focus.auth.controller

import com.capstone.focus.auth.dto.response.UserInfoResponse
import com.capstone.focus.auth.security.service.FocusMemberDetails

import com.capstone.focus.common.common.annotations.FocusGetMapping
import com.capstone.focus.common.common.annotations.FocusPostMapping
import com.capstone.focus.common.common.dto.ApiResponse
import com.capstone.focus.common.external.redis.RedisService
import com.capstone.focus.domain.MemberService
import com.capstone.focus.domain.base.util.ResponseUtil
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@RestController
@RequestMapping("/api/members")
@Tag(name = "회원 관련 API", description = "회원 정보 조회 및 로그아웃 API")
@SecurityRequirement(name = "bearerAuth")
class MemberController(
    private val memberService: MemberService,
    private val redisService: RedisService
) {

    @FocusGetMapping("/me", authenticated = true)
    @SwaggerApiResponse(responseCode = "200", description = "사용자 정보 조회 성공")
    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자의 정보를 조회합니다.")
    fun getCurrentMember(
        @AuthenticationPrincipal memberDetails: FocusMemberDetails
    ): ResponseEntity<ApiResponse.Success<UserInfoResponse>> {
        val userInfo = memberService.getMemberInfo(memberDetails.getMemberId())
        return ResponseUtil.success("사용자 정보 조회 성공", userInfo)
    }

    @FocusPostMapping("/logout", authenticated = true)
    @SwaggerApiResponse(responseCode = "200", description = "로그아웃 성공")
    @Operation(summary = "로그아웃", description = "Redis에서 Refresh Token을 삭제하여 로그아웃 처리합니다.")
    fun logout(
        @AuthenticationPrincipal memberDetails: FocusMemberDetails
    ): ResponseEntity<ApiResponse.Success<String>> {
        // 로그아웃 시 Redis에 저장된 해당 유저의 Refresh Token 삭제
        redisService.deleteRefreshToken(memberDetails.getMemberId())
        return ResponseUtil.success("로그아웃 성공", "로그아웃이 완료되었습니다.")
    }
}