package com.kmu_focus.focusandroid.feature.auth.data.repository

import com.kmu_focus.focusandroid.core.network.data.TokenRefreshService
import com.kmu_focus.focusandroid.core.network.domain.TokenStore
import com.kmu_focus.focusandroid.feature.auth.data.remote.AuthApi
import com.kmu_focus.focusandroid.feature.auth.data.remote.dto.KakaoLoginRequest
import com.kmu_focus.focusandroid.feature.auth.domain.model.AuthError
import com.kmu_focus.focusandroid.feature.auth.domain.repository.ServerAuthRepository
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.inject.Inject
import retrofit2.Response

class ServerAuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val tokenRefreshService: TokenRefreshService,
) : ServerAuthRepository {

    override suspend fun loginWithKakaoToken(
        kakaoAccessToken: String,
    ): Result<Unit> {
        return try {
            val response = authApi.kakaoLogin(
                KakaoLoginRequest(accessToken = kakaoAccessToken),
            )
            val body = response.body()
            val tokenData = body?.data

            when {
                response.isSuccessful && body?.success == true && tokenData != null -> {
                    tokenStore.save(
                        accessToken = tokenData.accessToken,
                        refreshToken = tokenData.refreshToken,
                    )
                    Result.success(Unit)
                }

                response.isSuccessful -> {
                    Result.failure(
                        AuthError.Network(
                            body?.message?.takeIf { it.isNotBlank() } ?: "서버 로그인 실패",
                        )
                    )
                }

                else -> {
                    Result.failure(AuthError.Network(response.extractErrorMessage("서버 로그인 실패")))
                }
            }
        } catch (exception: IOException) {
            Result.failure(
                AuthError.Network(
                    message = exception.message ?: "네트워크 오류",
                    cause = exception,
                )
            )
        } catch (throwable: Throwable) {
            Result.failure(
                AuthError.Unexpected(
                    message = throwable.message ?: "로그인 실패",
                    cause = throwable,
                )
            )
        }
    }

    override suspend fun validateStoredSession(): Result<Boolean> {
        return try {
            val accessToken = tokenStore.getAccessToken()
            val refreshToken = tokenStore.getRefreshToken()

            when {
                !accessToken.isNullOrBlank() && !isExpiredJwt(accessToken) -> Result.success(true)

                refreshToken.isNullOrBlank() -> {
                    tokenStore.clear()
                    Result.failure(
                        if (accessToken.isNullOrBlank()) {
                            AuthError.TokenMissing
                        } else {
                            AuthError.TokenExpired
                        },
                    )
                }

                tokenRefreshService.refresh() -> Result.success(true)

                else -> Result.failure(AuthError.TokenExpired)
            }
        } catch (exception: IOException) {
            Result.failure(
                AuthError.Network(
                    message = exception.message ?: "네트워크 오류",
                    cause = exception,
                )
            )
        } catch (throwable: Throwable) {
            Result.failure(
                AuthError.Unexpected(
                    message = throwable.message ?: "자동 로그인 실패",
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

    private fun isExpiredJwt(token: String): Boolean {
        val payload = decodeJwtPayload(token) ?: return true
        val exp = EXP_REGEX.find(payload)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?: return true

        val nowEpochSeconds = System.currentTimeMillis() / 1000
        return exp <= nowEpochSeconds + JWT_EXPIRY_SKEW_SECONDS
    }

    private fun decodeJwtPayload(token: String): String? {
        val payloadSegment = token.split('.').getOrNull(1) ?: return null
        val padded = payloadSegment + "=".repeat((4 - payloadSegment.length % 4) % 4)
        val decoded = runCatching { Base64.getUrlDecoder().decode(padded) }
            .getOrNull()
            ?: return null

        return String(decoded, StandardCharsets.UTF_8)
    }

    private companion object {
        val EXP_REGEX = """"exp"\s*:\s*(\d+)""".toRegex()
        const val JWT_EXPIRY_SKEW_SECONDS = 30L
    }
}
