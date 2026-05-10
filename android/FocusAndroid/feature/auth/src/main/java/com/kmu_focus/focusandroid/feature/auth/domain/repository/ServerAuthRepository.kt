package com.kmu_focus.focusandroid.feature.auth.domain.repository

interface ServerAuthRepository {
    suspend fun loginWithKakaoToken(kakaoAccessToken: String): Result<Unit>
    suspend fun validateStoredSession(): Result<Boolean>
}
