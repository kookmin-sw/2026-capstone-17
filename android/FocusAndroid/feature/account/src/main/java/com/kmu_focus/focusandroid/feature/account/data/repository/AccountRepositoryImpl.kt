package com.kmu_focus.focusandroid.feature.account.data.repository

import com.kmu_focus.focusandroid.core.network.domain.TokenStore
import com.kmu_focus.focusandroid.feature.account.data.oauth.ChzzkOAuthConnectUrlValidator
import com.kmu_focus.focusandroid.feature.account.data.remote.AccountApi
import com.kmu_focus.focusandroid.feature.account.data.remote.dto.toEntity
import com.kmu_focus.focusandroid.feature.account.domain.entity.ChzzkConnectionStatus
import com.kmu_focus.focusandroid.feature.account.domain.entity.UserProfile
import com.kmu_focus.focusandroid.feature.account.domain.model.AccountError
import com.kmu_focus.focusandroid.feature.account.domain.repository.AccountRepository
import java.io.IOException
import javax.inject.Inject
import retrofit2.Response

class AccountRepositoryImpl @Inject constructor(
    private val accountApi: AccountApi,
    private val tokenStore: TokenStore,
    private val chzzkOAuthConnectUrlValidator: ChzzkOAuthConnectUrlValidator,
) : AccountRepository {

    override suspend fun getCurrentUser(): Result<UserProfile> {
        return try {
            val response = accountApi.getCurrentUser()
            val body = response.body()
            val user = body?.data

            when {
                response.isSuccessful && body?.success == true && user != null -> {
                    Result.success(user.toEntity())
                }

                response.isSuccessful -> {
                    Result.failure(
                        AccountError.Network(
                            body?.message?.takeIf { it.isNotBlank() } ?: "내 정보 조회 실패",
                        )
                    )
                }

                else -> {
                    Result.failure(
                        AccountError.Network(
                            response.extractErrorMessage("내 정보 조회 실패"),
                        )
                    )
                }
            }
        } catch (exception: IOException) {
            Result.failure(
                AccountError.Network(
                    message = exception.message ?: "네트워크 오류",
                    cause = exception,
                )
            )
        } catch (throwable: Throwable) {
            Result.failure(
                AccountError.Unexpected(
                    message = throwable.message ?: "내 정보 조회 실패",
                    cause = throwable,
                )
            )
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            val response = accountApi.logout()
            val body = response.body()

            when {
                response.isSuccessful && body?.success == true -> {
                    tokenStore.clear()
                    Result.success(Unit)
                }

                response.isSuccessful -> {
                    Result.failure(
                        AccountError.Network(
                            body?.message?.takeIf { it.isNotBlank() } ?: "로그아웃 실패",
                        )
                    )
                }

                else -> {
                    Result.failure(
                        AccountError.Network(
                            response.extractErrorMessage("로그아웃 실패"),
                        )
                    )
                }
            }
        } catch (exception: IOException) {
            Result.failure(
                AccountError.Network(
                    message = exception.message ?: "네트워크 오류",
                    cause = exception,
                )
            )
        } catch (throwable: Throwable) {
            Result.failure(
                AccountError.Unexpected(
                    message = throwable.message ?: "로그아웃 실패",
                    cause = throwable,
                )
            )
        }
    }

    override suspend fun getChzzkConnectionStatus(): Result<ChzzkConnectionStatus> {
        return try {
            val response = accountApi.getChzzkConnectionStatus()
            val body = response.body()
            val status = body?.data

            when {
                response.isSuccessful && body?.success == true && status != null -> {
                    Result.success(status.toEntity())
                }

                response.isSuccessful -> {
                    Result.failure(
                        AccountError.Network(
                            body?.message?.takeIf { it.isNotBlank() } ?: "치지직 연동 상태 조회 실패",
                        )
                    )
                }

                else -> {
                    Result.failure(
                        AccountError.Network(
                            response.extractErrorMessage("치지직 연동 상태 조회 실패"),
                        ),
                    )
                }
            }
        } catch (exception: IOException) {
            Result.failure(
                AccountError.Network(
                    message = exception.message ?: "네트워크 오류",
                    cause = exception,
                )
            )
        } catch (throwable: Throwable) {
            Result.failure(
                AccountError.Unexpected(
                    message = throwable.message ?: "치지직 연동 상태 조회 실패",
                    cause = throwable,
                )
            )
        }
    }

    override suspend fun getChzzkConnectUrl(): Result<String> {
        return try {
            val response = accountApi.getChzzkConnectUrl()
            val body = response.body()
            val data = body?.data

            when {
                response.isSuccessful && body?.success == true && data != null && data.authUrl.isNotBlank() -> {
                    chzzkOAuthConnectUrlValidator.validate(data.authUrl)
                }

                response.isSuccessful -> {
                    Result.failure(
                        AccountError.Network(
                            body?.message?.takeIf { it.isNotBlank() } ?: "치지직 연동 URL 조회 실패",
                        )
                    )
                }

                else -> {
                    Result.failure(
                        AccountError.Network(
                            response.extractErrorMessage("치지직 연동 URL 조회 실패"),
                        ),
                    )
                }
            }
        } catch (exception: IOException) {
            Result.failure(
                AccountError.Network(
                    message = exception.message ?: "네트워크 오류",
                    cause = exception,
                )
            )
        } catch (throwable: Throwable) {
            Result.failure(
                AccountError.Unexpected(
                    message = throwable.message ?: "치지직 연동 URL 조회 실패",
                    cause = throwable,
                )
            )
        }
    }

    override suspend fun disconnectChzzk(): Result<Unit> {
        return try {
            val response = accountApi.disconnectChzzk()
            val body = response.body()

            when {
                response.isSuccessful && body?.success == true -> Result.success(Unit)
                response.isSuccessful -> {
                    Result.failure(
                        AccountError.Network(
                            body?.message?.takeIf { it.isNotBlank() } ?: "치지직 연동 해제 실패",
                        )
                    )
                }

                else -> {
                    Result.failure(
                        AccountError.Network(
                            response.extractErrorMessage("치지직 연동 해제 실패"),
                        ),
                    )
                }
            }
        } catch (exception: IOException) {
            Result.failure(
                AccountError.Network(
                    message = exception.message ?: "네트워크 오류",
                    cause = exception,
                )
            )
        } catch (throwable: Throwable) {
            Result.failure(
                AccountError.Unexpected(
                    message = throwable.message ?: "치지직 연동 해제 실패",
                    cause = throwable,
                )
            )
        }
    }

    private fun Response<*>.extractErrorMessage(defaultMessage: String): String {
        return errorBody()?.string()?.takeIf { it.isNotBlank() }
            ?: message().takeIf { it.isNotBlank() }
            ?: defaultMessage
    }
}
