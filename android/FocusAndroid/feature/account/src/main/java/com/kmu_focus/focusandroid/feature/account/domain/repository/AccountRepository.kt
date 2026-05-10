package com.kmu_focus.focusandroid.feature.account.domain.repository

import com.kmu_focus.focusandroid.feature.account.domain.entity.ChzzkConnectionStatus
import com.kmu_focus.focusandroid.feature.account.domain.entity.UserProfile

interface AccountRepository {
    suspend fun getCurrentUser(): Result<UserProfile>
    suspend fun logout(): Result<Unit>
    suspend fun getChzzkConnectionStatus(): Result<ChzzkConnectionStatus>
    suspend fun getChzzkConnectUrl(): Result<String>
    suspend fun disconnectChzzk(): Result<Unit>
}
