package com.kmu_focus.focusandroid.feature.auth.data.repository

import android.content.Context
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.AuthError as KakaoAuthError
import com.kakao.sdk.common.model.AuthErrorCause
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.common.model.KakaoSdkError
import com.kakao.sdk.user.UserApiClient
import com.kmu_focus.focusandroid.feature.auth.domain.model.AuthError
import com.kmu_focus.focusandroid.feature.auth.domain.repository.KakaoAuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Singleton
class KakaoAuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
) : KakaoAuthRepository {

    override suspend fun login(context: Any): Result<String> {
        val loginContext = context as? Context
            ?: return Result.failure(AuthError.InvalidContext)

        return suspendCancellableCoroutine { continuation ->
            val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
                val result = when {
                    token != null -> Result.success(token.accessToken)
                    error != null -> Result.failure(error.toAuthError())
                    else -> Result.failure(AuthError.Unexpected("카카오 로그인 실패"))
                }

                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            if (UserApiClient.instance.isKakaoTalkLoginAvailable(applicationContext)) {
                UserApiClient.instance.loginWithKakaoTalk(loginContext, callback = callback)
            } else {
                UserApiClient.instance.loginWithKakaoAccount(loginContext, callback = callback)
            }
        }
    }

    override suspend fun validateToken(): Result<Boolean> {
        return suspendCancellableCoroutine { continuation ->
            UserApiClient.instance.accessTokenInfo { info, error ->
                val result = when {
                    info != null -> Result.success(true)
                    error != null -> Result.failure(error.toAuthError())
                    else -> Result.failure(AuthError.TokenMissing)
                }

                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        }
    }

    private fun Throwable.toAuthError(): AuthError {
        return when (this) {
            is AuthError -> this
            is ClientError -> when (reason) {
                ClientErrorCause.Cancelled -> AuthError.UserCancelled
                ClientErrorCause.TokenNotFound -> AuthError.TokenMissing
                else -> AuthError.Unexpected(msg.ifBlank { message ?: "로그인 실패" }, this)
            }

            is KakaoAuthError -> when (reason) {
                AuthErrorCause.InvalidGrant,
                AuthErrorCause.Unauthorized,
                AuthErrorCause.AccessDenied,
                -> AuthError.TokenExpired

                AuthErrorCause.ServerError -> {
                    AuthError.Network(message ?: "카카오 서버 오류", this)
                }

                else -> AuthError.Unexpected(message ?: "로그인 실패", this)
            }

            is KakaoSdkError -> {
                if (isInvalidTokenError()) {
                    AuthError.TokenExpired
                } else {
                    AuthError.Unexpected(message ?: "로그인 실패", this)
                }
            }

            is IOException -> AuthError.Network(message ?: "네트워크 오류", this)
            else -> AuthError.Unexpected(message ?: "로그인 실패", this)
        }
    }
}
