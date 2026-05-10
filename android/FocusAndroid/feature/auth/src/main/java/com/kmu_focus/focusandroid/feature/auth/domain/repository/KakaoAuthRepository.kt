package com.kmu_focus.focusandroid.feature.auth.domain.repository

interface KakaoAuthRepository {
    suspend fun login(context: Any): Result<String>
    suspend fun validateToken(): Result<Boolean>
}
