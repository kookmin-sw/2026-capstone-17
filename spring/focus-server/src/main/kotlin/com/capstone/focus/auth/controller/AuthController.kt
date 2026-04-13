package com.capstone.focus.auth.controller

import com.capstone.focus.auth.dto.request.KakaoLoginRequest
import com.capstone.focus.auth.dto.request.RefreshTokenRequest
import com.capstone.focus.auth.dto.response.AuthUrlResponse
import com.capstone.focus.auth.dto.response.TokenResponse
import com.capstone.focus.auth.service.AuthService
import com.capstone.focus.common.common.annotations.FocusGetMapping
import com.capstone.focus.common.common.annotations.FocusPostMapping
import com.capstone.focus.common.common.dto.ApiResponse
import com.capstone.focus.common.exception.ErrorTitle
import com.capstone.focus.common.exception.annotation.CustomFailResponseAnnotation
import com.capstone.focus.domain.base.util.ResponseUtil
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@RestController
@RequestMapping("/api/auth")
@Tag(name = "인증 관련 API", description = "카카오 로그인 및 토큰 관리 API")
class AuthController(
    private val authService: AuthService
) {

    @FocusGetMapping("/kakao/url")
    @SwaggerApiResponse(responseCode = "200", description = "카카오 로그인 URL 조회 성공")
    @Operation(summary = "카카오 로그인 URL 조회", description = "프론트엔드에서 리다이렉트할 카카오 인증 URL을 반환합니다.")
    fun getKakaoAuthUrl(): ResponseEntity<ApiResponse.Success<AuthUrlResponse>> {
        return ResponseUtil.success("카카오 로그인 URL 조회 성공", authService.getKakaoAuthUrl())
    }

    @FocusPostMapping("/kakao/login")
    @SwaggerApiResponse(responseCode = "200", description = "카카오 로그인 성공")
    @Operation(summary = "카카오 로그인", description = "카카오 인증 코드를 받아 로그인/회원가입을 처리하고 JWT를 발급합니다.")
    @CustomFailResponseAnnotation(ErrorTitle.InvalidInputValue) // 입력값 오류
    @CustomFailResponseAnnotation(ErrorTitle.ExternalServerError) // 카카오 서버 오류
    fun kakaoLogin(
        @RequestBody @Valid request: KakaoLoginRequest
    ): ResponseEntity<ApiResponse.Success<TokenResponse>> {
        return ResponseUtil.success("카카오 로그인 성공", authService.kakaoLogin(request))
    }

    @FocusPostMapping("/refresh")
    @SwaggerApiResponse(responseCode = "200", description = "토큰 재발급 성공")
    @Operation(summary = "토큰 재발급", description = "Refresh Token을 이용해 Access Token을 재발급합니다.")
    @CustomFailResponseAnnotation(ErrorTitle.InvalidToken) // 토큰 위변조
    @CustomFailResponseAnnotation(ErrorTitle.ExpiredToken) // 토큰 만료
    @CustomFailResponseAnnotation(ErrorTitle.NotFoundUser) // 유저 없음
    fun refresh(
        @RequestBody @Valid request: RefreshTokenRequest
    ): ResponseEntity<ApiResponse.Success<TokenResponse>> {
        return ResponseUtil.success("토큰 재발급 성공", authService.refresh(request))
    }
}