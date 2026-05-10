package com.kmu_focus.focusandroid.core.network.data

import com.kmu_focus.focusandroid.core.network.domain.TokenStore
import com.kmu_focus.focusandroid.core.network.dto.ApiResponse
import com.kmu_focus.focusandroid.core.network.dto.AppTokenResponse
import com.kmu_focus.focusandroid.core.network.dto.RefreshTokenRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

class TokenRefreshService(
    private val tokenStore: TokenStore,
    baseUrl: String,
    okHttpClient: OkHttpClient = OkHttpClient.Builder().build(),
) {

    private val mutex = Mutex()
    private val refreshApi: RefreshApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RefreshApi::class.java)

    suspend fun refresh(): Boolean = mutex.withLock {
        val refreshToken = tokenStore.getRefreshToken() ?: return@withLock false
        val response = runCatching {
            refreshApi.refresh(RefreshTokenRequest(refreshToken))
        }.getOrElse {
            return@withLock false
        }

        when {
            response.code() == NO_CONTENT_CODE -> {
                tokenStore.clear()
                false
            }

            response.isSuccessful -> {
                val tokenData = response.body()?.data ?: return@withLock false
                tokenStore.save(tokenData.accessToken, tokenData.refreshToken)
                true
            }

            response.code() == UNAUTHORIZED_CODE -> {
                tokenStore.clear()
                false
            }

            else -> false
        }
    }

    internal interface RefreshApi {
        @POST("/api/auth/refresh")
        suspend fun refresh(
            @Body body: RefreshTokenRequest,
        ): Response<ApiResponse<AppTokenResponse>>
    }

    private companion object {
        const val UNAUTHORIZED_CODE = 401
        const val NO_CONTENT_CODE = 204
    }
}
