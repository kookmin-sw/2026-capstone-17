package com.kmu_focus.focusandroid.feature.account.data.remote

import com.kmu_focus.focusandroid.core.network.dto.ApiResponse
import com.kmu_focus.focusandroid.feature.account.data.remote.dto.ChzzkConnectResponseDto
import com.kmu_focus.focusandroid.feature.account.data.remote.dto.ChzzkConnectionStatusResponseDto
import com.kmu_focus.focusandroid.feature.account.data.remote.dto.UserInfoResponseDto
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST

interface AccountApi {
    @GET("/api/members/me")
    suspend fun getCurrentUser(): Response<ApiResponse<UserInfoResponseDto>>

    @POST("/api/members/logout")
    suspend fun logout(): Response<ApiResponse<String>>

    @GET("/api/v1/platforms/chzzk/status")
    suspend fun getChzzkConnectionStatus(): Response<ApiResponse<ChzzkConnectionStatusResponseDto>>

    @GET("/api/v1/platforms/chzzk/connect")
    suspend fun getChzzkConnectUrl(): Response<ApiResponse<ChzzkConnectResponseDto>>

    @DELETE("/api/v1/platforms/chzzk/connection")
    suspend fun disconnectChzzk(): Response<ApiResponse<Unit>>
}
