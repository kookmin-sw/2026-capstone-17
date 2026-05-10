package com.kmu_focus.focusandroid.feature.auth.domain.usecase

import com.kmu_focus.focusandroid.feature.auth.domain.model.AuthError
import com.kmu_focus.focusandroid.feature.auth.domain.repository.KakaoAuthRepository
import javax.inject.Inject

class KakaoLoginUseCase @Inject constructor(
    private val repository: KakaoAuthRepository,
) {
    suspend operator fun invoke(context: Any): Result<String> {
        return runCatching { repository.login(context) }
            .getOrElse {
                Result.failure(
                    AuthError.Unexpected(
                        message = it.message ?: "로그인 실패",
                        cause = it,
                    )
                )
            }
    }
}
