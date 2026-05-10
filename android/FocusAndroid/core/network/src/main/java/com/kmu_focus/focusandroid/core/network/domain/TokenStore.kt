package com.kmu_focus.focusandroid.core.network.domain

interface TokenStore {
    suspend fun save(accessToken: String, refreshToken: String)
    suspend fun getAccessToken(): String?
    suspend fun getRefreshToken(): String?
    suspend fun clear()
}
