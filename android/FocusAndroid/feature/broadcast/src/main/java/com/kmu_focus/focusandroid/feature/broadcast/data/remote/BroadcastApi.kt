package com.kmu_focus.focusandroid.feature.broadcast.data.remote

import com.kmu_focus.focusandroid.core.network.dto.ApiResponse
import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.BroadcastResponseDto
import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.CreateBroadcastRequestDto
import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.PageBroadcastResponseDto
import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.StartBroadcastRequestDto
import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.UpdateBroadcastRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface BroadcastApi {
    @POST("/api/v1/broadcasts")
    suspend fun createBroadcast(
        @Body request: CreateBroadcastRequestDto,
    ): Response<ApiResponse<BroadcastResponseDto>>

    @POST("/api/v1/broadcasts/{broadcastId}/start")
    suspend fun startBroadcast(
        @Path("broadcastId") broadcastId: String,
        @Body request: StartBroadcastRequestDto,
    ): Response<ApiResponse<BroadcastResponseDto>>

    @POST("/api/v1/broadcasts/{broadcastId}/stop")
    suspend fun stopBroadcast(
        @Path("broadcastId") broadcastId: String,
    ): Response<ApiResponse<BroadcastResponseDto>>

    @GET("/api/v1/broadcasts")
    suspend fun getBroadcastList(
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): Response<ApiResponse<PageBroadcastResponseDto>>

    @GET("/api/v1/broadcasts/{broadcastId}")
    suspend fun getBroadcastDetail(
        @Path("broadcastId") broadcastId: String,
    ): Response<ApiResponse<BroadcastResponseDto>>

    @PUT("/api/v1/broadcasts/{broadcastId}")
    suspend fun updateBroadcast(
        @Path("broadcastId") broadcastId: String,
        @Body request: UpdateBroadcastRequestDto,
    ): Response<ApiResponse<BroadcastResponseDto>>

    @DELETE("/api/v1/broadcasts/{broadcastId}")
    suspend fun deleteBroadcast(
        @Path("broadcastId") broadcastId: String,
    ): Response<ApiResponse<Unit>>

    @POST("/api/v1/broadcasts/{broadcastId}/streamer-heartbeat")
    suspend fun streamerHeartbeat(
        @Path("broadcastId") broadcastId: String,
    ): Response<ApiResponse<Unit>>

    @POST("/api/v1/broadcasts/{broadcastId}/heartbeat")
    suspend fun viewerHeartbeat(
        @Path("broadcastId") broadcastId: String,
    ): Response<ApiResponse<Unit>>
}
