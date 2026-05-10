package com.kmu_focus.focusandroid.feature.auth.data.remote

import com.kmu_focus.focusandroid.core.network.dto.ApiResponse
import com.kmu_focus.focusandroid.core.network.dto.AppTokenResponse
import com.kmu_focus.focusandroid.feature.auth.data.remote.dto.KakaoLoginRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("/api/auth/kakao/login")
    suspend fun kakaoLogin(
        @Body request: KakaoLoginRequest,
    ): Response<ApiResponse<AppTokenResponse>>
}
