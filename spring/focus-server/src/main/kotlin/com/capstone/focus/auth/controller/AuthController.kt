package com.capstone.focus.auth.controller

import com.capstone.focus.auth.dto.request.KakaoLoginRequest
import com.capstone.focus.auth.dto.request.RefreshTokenRequest
import com.capstone.focus.auth.dto.response.TokenResponse
import com.capstone.focus.auth.service.AuthService
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
@Tag(name = "인증 관리 API", description = "카카오 로그인 및 토큰 관리 API")
class AuthController(
    private val authService: AuthService
) {

    @FocusPostMapping("/kakao/login")
    @SwaggerApiResponse(responseCode = "200", description = "카카오 로그인 성공")
    @Operation(
        summary = "카카오 로그인",
        description = "카카오 SDK에서 발급받은 access token으로 로그인/회원가입을 처리하고 JWT를 발급합니다."
    )
    @CustomFailResponseAnnotation(ErrorTitle.InvalidInputValue)
    @CustomFailResponseAnnotation(ErrorTitle.ExternalServerError)
    fun kakaoLogin(
        @RequestBody @Valid request: KakaoLoginRequest
    ): ResponseEntity<ApiResponse.Success<TokenResponse>> {
        return ResponseUtil.success("카카오 로그인 성공", authService.kakaoLogin(request))
    }

    @FocusPostMapping("/refresh")
    @SwaggerApiResponse(responseCode = "200", description = "토큰 재발급 성공")
    @Operation(summary = "토큰 재발급", description = "Refresh Token을 이용해 Access Token을 재발급합니다.")
    @CustomFailResponseAnnotation(ErrorTitle.InvalidToken)
    @CustomFailResponseAnnotation(ErrorTitle.ExpiredToken)
    @CustomFailResponseAnnotation(ErrorTitle.NotFoundUser)
    fun refresh(
        @RequestBody @Valid request: RefreshTokenRequest
    ): ResponseEntity<ApiResponse.Success<TokenResponse>> {
        return ResponseUtil.success("토큰 재발급 성공", authService.refresh(request))
    }
}
