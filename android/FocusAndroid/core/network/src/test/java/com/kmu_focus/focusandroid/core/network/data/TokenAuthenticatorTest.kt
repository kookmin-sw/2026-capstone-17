package com.kmu_focus.focusandroid.core.network.data

import com.kmu_focus.focusandroid.core.network.domain.SessionExpiredHandler
import com.kmu_focus.focusandroid.core.network.domain.TokenStore
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TokenAuthenticatorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var tokenStore: TokenStore
    private lateinit var tokenRefreshService: TokenRefreshService
    private lateinit var sessionExpiredHandler: SessionExpiredHandler
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        tokenStore = mockk(relaxed = true)
        tokenRefreshService = mockk(relaxed = true)
        sessionExpiredHandler = mockk(relaxed = true)

        client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .authenticator(
                TokenAuthenticator(tokenStore, tokenRefreshService, sessionExpiredHandler),
            )
            .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // --- 리프레시 성공 ---

    @Test
    fun `401 응답 시 토큰 리프레시를 시도하고 재요청한다`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns "expired_token" andThen "new_token"
        coEvery { tokenRefreshService.refresh() } returns true

        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("success"))

        val request = Request.Builder()
            .url(mockWebServer.url("/api/data"))
            .build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(2, mockWebServer.requestCount)

        val firstRequest = mockWebServer.takeRequest()
        val secondRequest = mockWebServer.takeRequest()
        assertEquals("Bearer expired_token", firstRequest.getHeader("Authorization"))
        assertEquals("Bearer new_token", secondRequest.getHeader("Authorization"))
    }

    @Test
    fun `리프레시 성공 시 세션 만료를 알리지 않는다`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns "expired_token" andThen "new_token"
        coEvery { tokenRefreshService.refresh() } returns true

        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(mockWebServer.url("/api/data"))
            .build()
        client.newCall(request).execute()

        verify(exactly = 0) { sessionExpiredHandler.onSessionExpired() }
    }

    // --- 리프레시 실패 → 세션 만료 ---

    @Test
    fun `401 응답 후 리프레시 실패 시 401을 그대로 반환한다`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns "expired_token"
        coEvery { tokenRefreshService.refresh() } returns false

        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        val request = Request.Builder()
            .url(mockWebServer.url("/api/data"))
            .build()
        val response = client.newCall(request).execute()

        assertEquals(401, response.code)
        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun `리프레시 실패 시 세션 만료를 알린다`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns "expired_token"
        coEvery { tokenRefreshService.refresh() } returns false

        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        val request = Request.Builder()
            .url(mockWebServer.url("/api/data"))
            .build()
        client.newCall(request).execute()

        verify(exactly = 1) { sessionExpiredHandler.onSessionExpired() }
    }

    // --- 인증 불필요 경로 스킵 ---

    @Test
    fun `로그인 경로의 401은 리프레시하지 않고 세션 만료도 알리지 않는다`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns "some_token"

        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        val request = Request.Builder()
            .url(mockWebServer.url("/api/auth/kakao/login"))
            .build()
        val response = client.newCall(request).execute()

        assertEquals(401, response.code)
        verify(exactly = 0) { sessionExpiredHandler.onSessionExpired() }
    }

    @Test
    fun `리프레시 경로의 401은 리프레시하지 않고 세션 만료도 알리지 않는다`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns "some_token"

        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        val request = Request.Builder()
            .url(mockWebServer.url("/api/auth/refresh"))
            .build()
        val response = client.newCall(request).execute()

        assertEquals(401, response.code)
        verify(exactly = 0) { sessionExpiredHandler.onSessionExpired() }
    }

    // --- 정상 응답 ---

    @Test
    fun `200 응답은 Authenticator를 거치지 않는다`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns "valid_token"

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val request = Request.Builder()
            .url(mockWebServer.url("/api/data"))
            .build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(1, mockWebServer.requestCount)
        verify(exactly = 0) { sessionExpiredHandler.onSessionExpired() }
    }

    // --- 재시도 제한 ---

    @Test
    fun `연속 401 시 최대 재시도 횟수를 초과하면 포기하고 세션 만료를 알린다`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns "token"
        coEvery { tokenRefreshService.refresh() } returns true

        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        val request = Request.Builder()
            .url(mockWebServer.url("/api/data"))
            .build()
        val response = client.newCall(request).execute()

        assertEquals(401, response.code)
    }
}
