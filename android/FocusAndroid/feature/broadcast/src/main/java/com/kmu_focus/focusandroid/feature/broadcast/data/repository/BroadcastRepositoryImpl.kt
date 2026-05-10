package com.kmu_focus.focusandroid.feature.broadcast.data.repository

import com.kmu_focus.focusandroid.core.network.dto.ApiResponse
import com.kmu_focus.focusandroid.feature.broadcast.data.mapper.toEntity
import com.kmu_focus.focusandroid.feature.broadcast.data.remote.BroadcastApi
import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.BroadcastResponseDto
import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.CreateBroadcastRequestDto
import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.PageBroadcastResponseDto
import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.StartBroadcastRequestDto
import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.UpdateBroadcastRequestDto
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast
import com.kmu_focus.focusandroid.feature.broadcast.domain.repository.BroadcastRepository
import javax.inject.Inject
import retrofit2.Response

class BroadcastRepositoryImpl @Inject constructor(
    private val broadcastApi: BroadcastApi,
) : BroadcastRepository {

    override suspend fun createBroadcast(title: String): Result<Broadcast> {
        return execute(
            apiCall = {
                broadcastApi.createBroadcast(
                    CreateBroadcastRequestDto(title = title),
                )
            },
            successMapper = BroadcastResponseDto::toEntity,
            emptyDataMessage = "방송 생성 결과가 비어 있습니다",
            failureMessage = "방송 생성 실패",
        )
    }

    override suspend fun startBroadcast(
        broadcastId: String,
        avatarId: String,
    ): Result<Broadcast> {
        return execute(
            apiCall = {
                broadcastApi.startBroadcast(
                    broadcastId = broadcastId,
                    request = StartBroadcastRequestDto(avatarId = avatarId),
                )
            },
            successMapper = BroadcastResponseDto::toEntity,
            emptyDataMessage = "방송 시작 결과가 비어 있습니다",
            failureMessage = "방송 시작 실패",
        )
    }

    override suspend fun stopBroadcast(broadcastId: String): Result<Broadcast> {
        return execute(
            apiCall = { broadcastApi.stopBroadcast(broadcastId) },
            successMapper = BroadcastResponseDto::toEntity,
            emptyDataMessage = "방송 종료 결과가 비어 있습니다",
            failureMessage = "방송 종료 실패",
        )
    }

    override suspend fun getBroadcastList(
        page: Int,
        size: Int,
    ): Result<List<Broadcast>> {
        return execute(
            apiCall = { broadcastApi.getBroadcastList(page = page, size = size) },
            successMapper = { it.toEntityList() },
            emptyDataMessage = "방송 목록 응답이 비어 있습니다",
            failureMessage = "방송 목록 조회 실패",
        )
    }

    override suspend fun getBroadcastDetail(broadcastId: String): Result<Broadcast> {
        return execute(
            apiCall = { broadcastApi.getBroadcastDetail(broadcastId) },
            successMapper = BroadcastResponseDto::toEntity,
            emptyDataMessage = "방송 상세 응답이 비어 있습니다",
            failureMessage = "방송 상세 조회 실패",
        )
    }

    override suspend fun updateBroadcast(
        broadcastId: String,
        title: String,
    ): Result<Broadcast> {
        return execute(
            apiCall = {
                broadcastApi.updateBroadcast(
                    broadcastId = broadcastId,
                    request = UpdateBroadcastRequestDto(title = title),
                )
            },
            successMapper = BroadcastResponseDto::toEntity,
            emptyDataMessage = "방송 수정 결과가 비어 있습니다",
            failureMessage = "방송 수정 실패",
        )
    }

    override suspend fun deleteBroadcast(broadcastId: String): Result<Unit> {
        return executeUnit(
            apiCall = { broadcastApi.deleteBroadcast(broadcastId) },
            failureMessage = "방송 삭제 실패",
        )
    }

    override suspend fun sendStreamerHeartbeat(broadcastId: String): Result<Unit> {
        return executeUnit(
            apiCall = { broadcastApi.streamerHeartbeat(broadcastId) },
            failureMessage = "스트리머 하트비트 전송 실패",
        )
    }

    private suspend fun <T, R> execute(
        apiCall: suspend () -> Response<ApiResponse<T>>,
        successMapper: (T) -> R,
        emptyDataMessage: String,
        failureMessage: String,
    ): Result<R> {
        return try {
            val response = apiCall()
            val body = response.body()
            val data = body?.data

            when {
                response.isSuccessful && body?.success == true && data != null -> {
                    Result.success(successMapper(data))
                }

                response.isSuccessful -> {
                    Result.failure(
                        IllegalStateException(
                            body?.message?.takeIf { it.isNotBlank() } ?: emptyDataMessage,
                        )
                    )
                }

                else -> {
                    Result.failure(
                        IllegalStateException(response.extractErrorMessage(failureMessage)),
                    )
                }
            }
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }

    private suspend fun executeUnit(
        apiCall: suspend () -> Response<ApiResponse<Unit>>,
        failureMessage: String,
    ): Result<Unit> {
        return try {
            val response = apiCall()
            val body = response.body()

            when {
                response.isSuccessful && body?.success == true -> Result.success(Unit)
                response.isSuccessful -> {
                    Result.failure(
                        IllegalStateException(
                            body?.message?.takeIf { it.isNotBlank() } ?: failureMessage,
                        )
                    )
                }

                else -> {
                    Result.failure(
                        IllegalStateException(response.extractErrorMessage(failureMessage)),
                    )
                }
            }
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }

    private fun PageBroadcastResponseDto.toEntityList(): List<Broadcast> {
        return content.map(BroadcastResponseDto::toEntity)
    }

    private fun Response<*>.extractErrorMessage(defaultMessage: String): String {
        return errorBody()?.string()?.takeIf { it.isNotBlank() }
            ?: message().takeIf { it.isNotBlank() }
            ?: defaultMessage
    }
}
