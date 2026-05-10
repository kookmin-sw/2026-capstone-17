package com.kmu_focus.focusandroid.core.network.data

import com.kmu_focus.focusandroid.core.network.domain.TokenStore
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var tokenStore: TokenStore
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        tokenStore = mockk(relaxed = true)

        val interceptor = AuthInterceptor(tokenStore)
        client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `요청에 Bearer 토큰 헤더를 추가한다`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns "my_access_token"
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(mockWebServer.url("/api/data"))
            .build()
        client.newCall(request).execute()

        val recorded = mockWebServer.takeRequest()
        assertEquals("Bearer my_access_token", recorded.getHeader("Authorization"))
    }

    @Test
    fun `로그인 경로는 토큰 헤더를 추가하지 않는다`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns "my_access_token"
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(mockWebServer.url("/api/auth/kakao/login"))
            .build()
        client.newCall(request).execute()

        val recorded = mockWebServer.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `리프레시 경로는 토큰 헤더를 추가하지 않는다`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns "my_access_token"
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(mockWebServer.url("/api/auth/refresh"))
            .build()
        client.newCall(request).execute()

        val recorded = mockWebServer.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `토큰이 없으면 헤더 없이 요청한다`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns null
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(mockWebServer.url("/api/data"))
            .build()
        client.newCall(request).execute()

        val recorded = mockWebServer.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }
}
