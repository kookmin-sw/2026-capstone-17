package com.kmu_focus.focusandroid.core.network.data

import com.kmu_focus.focusandroid.core.network.domain.TokenStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        if (path.contains(LOGIN_PATH) || path.contains(REFRESH_PATH)) {
            return chain.proceed(request)
        }

        // OkHttp Interceptor는 suspend를 지원하지 않아 토큰 조회를 동기 브리지로 감싼다.
        val accessToken = runBlocking { tokenStore.getAccessToken() }
        val authorizedRequest = request.newBuilder().apply {
            if (!accessToken.isNullOrBlank()) {
                header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$accessToken")
            }
        }.build()

        return chain.proceed(authorizedRequest)
    }

    private companion object {
        const val LOGIN_PATH = "/api/auth/kakao/login"
        const val REFRESH_PATH = "/api/auth/refresh"
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }
}
