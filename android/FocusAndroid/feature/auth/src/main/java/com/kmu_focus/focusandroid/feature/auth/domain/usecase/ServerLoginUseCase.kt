package com.kmu_focus.focusandroid.feature.auth.domain.usecase

import com.kmu_focus.focusandroid.feature.auth.domain.model.AuthError
import com.kmu_focus.focusandroid.feature.auth.domain.repository.ServerAuthRepository
import com.kmu_focus.focusandroid.feature.auth.domain.session.AuthSessionManager
import javax.inject.Inject

class ServerLoginUseCase @Inject constructor(
    private val serverAuthRepository: ServerAuthRepository,
    private val authSessionManager: AuthSessionManager,
) {
    suspend operator fun invoke(kakaoAccessToken: String): Result<Unit> {
        if (kakaoAccessToken.isBlank()) {
            authSessionManager.update(false)
            return Result.failure(AuthError.InvalidContext)
        }

        return try {
            val result = serverAuthRepository.loginWithKakaoToken(kakaoAccessToken)
            authSessionManager.update(result.isSuccess)
            result
        } catch (throwable: Throwable) {
            authSessionManager.update(false)
            Result.failure(
                AuthError.Unexpected(
                    message = throwable.message ?: "로그인 실패",
                    cause = throwable,
                )
            )
        }
    }
}
