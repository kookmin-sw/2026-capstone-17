package com.kmu_focus.focusandroid.core.network.data

import com.kmu_focus.focusandroid.core.network.domain.TokenStore
import com.kmu_focus.focusandroid.core.network.dto.ApiResponse
import com.kmu_focus.focusandroid.core.network.dto.AppTokenResponse
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TokenRefreshServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var tokenStore: TokenStore
    private lateinit var service: TokenRefreshService
    private val gson = Gson()

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        tokenStore = mockk(relaxed = true)
        service = TokenRefreshService(tokenStore, mockWebServer.url("/").toString())
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `리프레시 성공 시 새 토큰을 저장하고 true를 반환한다`() = runTest {
        coEvery { tokenStore.getRefreshToken() } returns "old_refresh"

        val body = ApiResponse(
            success = true,
            message = "OK",
            data = AppTokenResponse("new_access", "new_refresh"),
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(gson.toJson(body))
                .addHeader("Content-Type", "application/json"),
        )

        val result = service.refresh()

        assertTrue(result)
        coVerify { tokenStore.save("new_access", "new_refresh") }
    }

    @Test
    fun `리프레시 토큰이 없으면 false를 반환한다`() = runTest {
        coEvery { tokenStore.getRefreshToken() } returns null

        val result = service.refresh()

        assertFalse(result)
    }

    @Test
    fun `서버가 401을 반환하면 토큰을 삭제하고 false를 반환한다`() = runTest {
        coEvery { tokenStore.getRefreshToken() } returns "expired_refresh"
        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        val result = service.refresh()

        assertFalse(result)
        coVerify { tokenStore.clear() }
    }

    @Test
    fun `서버 에러(5xx) 시 false를 반환한다`() = runTest {
        coEvery { tokenStore.getRefreshToken() } returns "some_refresh"
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val result = service.refresh()

        assertFalse(result)
    }

    @Test
    fun `서버가 204를 반환하면 토큰을 삭제하고 false를 반환한다`() = runTest {
        coEvery { tokenStore.getRefreshToken() } returns "some_refresh"
        mockWebServer.enqueue(MockResponse().setResponseCode(204))

        val result = service.refresh()

        assertFalse(result)
        coVerify { tokenStore.clear() }
    }

    @Test
    fun `리프레시 요청 본문에 refreshToken이 포함된다`() = runTest {
        coEvery { tokenStore.getRefreshToken() } returns "my_refresh_token"

        val body = ApiResponse(
            success = true,
            message = "OK",
            data = AppTokenResponse("access", "refresh"),
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(gson.toJson(body))
                .addHeader("Content-Type", "application/json"),
        )

        service.refresh()

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.body.readUtf8().contains("my_refresh_token"))
    }
}
