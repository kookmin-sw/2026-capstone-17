package com.kmu_focus.focusandroid.core.network.data

import com.kmu_focus.focusandroid.core.network.domain.SessionExpiredHandler
import com.kmu_focus.focusandroid.core.network.domain.TokenStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val tokenRefreshService: TokenRefreshService,
    private val sessionExpiredHandler: SessionExpiredHandler,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request.url.encodedPath
        if (path.contains(LOGIN_PATH) || path.contains(REFRESH_PATH)) {
            return null
        }
        if (responseCount(response) >= MAX_RETRY_COUNT) {
            sessionExpiredHandler.onSessionExpired()
            return null
        }

        // OkHttp Authenticator는 suspend를 지원하지 않아 리프레시를 동기 브리지로 감싼다.
        val refreshed = runBlocking { tokenRefreshService.refresh() }
        if (!refreshed) {
            sessionExpiredHandler.onSessionExpired()
            return null
        }

        val newAccessToken = runBlocking { tokenStore.getAccessToken() } ?: return null
        return response.request.newBuilder()
            .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$newAccessToken")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var current = response.priorResponse
        while (current != null) {
            count++
            current = current.priorResponse
        }
        return count
    }

    private companion object {
        const val LOGIN_PATH = "/api/auth/kakao/login"
        const val REFRESH_PATH = "/api/auth/refresh"
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer "
        const val MAX_RETRY_COUNT = 2
    }
}
