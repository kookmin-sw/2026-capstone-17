package com.kmu_focus.focusandroid.feature.account.data.repository

import com.kmu_focus.focusandroid.core.network.domain.TokenStore
import com.kmu_focus.focusandroid.core.network.dto.ApiResponse
import com.kmu_focus.focusandroid.feature.account.data.oauth.ChzzkOAuthConfig
import com.kmu_focus.focusandroid.feature.account.data.oauth.ChzzkOAuthConnectUrlValidator
import com.kmu_focus.focusandroid.feature.account.data.remote.AccountApi
import com.kmu_focus.focusandroid.feature.account.data.remote.dto.ChzzkConnectResponseDto
import com.kmu_focus.focusandroid.feature.account.data.remote.dto.ChzzkConnectionStatusResponseDto
import com.kmu_focus.focusandroid.feature.account.domain.model.AccountError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class AccountRepositoryImplTest {

    private lateinit var accountApi: AccountApi
    private lateinit var tokenStore: TokenStore
    private lateinit var validator: ChzzkOAuthConnectUrlValidator
    private lateinit var repository: AccountRepositoryImpl

    @Before
    fun setup() {
        accountApi = mockk()
        tokenStore = mockk(relaxed = true)
        validator = ChzzkOAuthConnectUrlValidator(
            config = ChzzkOAuthConfig(
                clientId = "c1f78a8f-aee0-487d-ae80-36ff9e627092",
                redirectUri = "http://3.35.202.126:8080/api/v1/platforms/chzzk/callback",
                authBaseUrl = "https://chzzk.naver.com/account-interlock",
            ),
        )
        repository = AccountRepositoryImpl(accountApi, tokenStore, validator)
    }

    @Test
    fun `치지직 연동 상태 조회 성공 시 엔티티로 변환한다`() = runTest {
        coEvery { accountApi.getChzzkConnectionStatus() } returns Response.success(
            ApiResponse(
                success = true,
                message = "OK",
                data = ChzzkConnectionStatusResponseDto(
                    connected = true,
                    channelId = "channel-1",
                    channelName = "포커스 채널",
                    watchUrl = "https://chzzk.naver.com/live/1",
                    accessTokenExpiresAt = "2026-05-08T12:00:00",
                    connectedAt = "2026-05-08T11:00:00",
                ),
            ),
        )

        val result = repository.getChzzkConnectionStatus()

        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull()?.connected)
        assertEquals("channel-1", result.getOrNull()?.channelId)
        assertEquals("포커스 채널", result.getOrNull()?.channelName)
        assertEquals("https://chzzk.naver.com/live/1", result.getOrNull()?.watchUrl)
    }

    @Test
    fun `치지직 연동 URL 조회 성공 시 검증된 authUrl을 반환한다`() = runTest {
        val authUrl = buildAuthUrl()
        coEvery { accountApi.getChzzkConnectUrl() } returns Response.success(
            ApiResponse(
                success = true,
                message = "OK",
                data = ChzzkConnectResponseDto(authUrl = authUrl),
            ),
        )

        val result = repository.getChzzkConnectUrl()

        assertTrue(result.isSuccess)
        assertEquals(authUrl, result.getOrNull())
        coVerify(exactly = 1) { accountApi.getChzzkConnectUrl() }
    }

    @Test
    fun `치지직 연동 URL의 redirectUri가 다르면 실패한다`() = runTest {
        coEvery { accountApi.getChzzkConnectUrl() } returns Response.success(
            ApiResponse(
                success = true,
                message = "OK",
                data = ChzzkConnectResponseDto(
                    authUrl =
                        "https://chzzk.naver.com/account-interlock?" +
                            "clientId=c1f78a8f-aee0-487d-ae80-36ff9e627092&" +
                            "redirectUri=https%3A%2F%2Fexample.com%2Fcallback&" +
                            "state=test-state-123",
                ),
            ),
        )

        val result = repository.getChzzkConnectUrl()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AccountError.Configuration)
        assertEquals(
            "치지직 OAuth URL의 redirectUri가 local.properties 설정과 다릅니다.",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun `치지직 연동 URL 조회가 500이면 실패한다`() = runTest {
        coEvery { accountApi.getChzzkConnectUrl() } returns Response.error(
            500,
            "Internal Server Error".toResponseBody(),
        )

        val result = repository.getChzzkConnectUrl()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AccountError.Network)
    }

    @Test
    fun `치지직 연동 해제 성공 시 성공을 반환한다`() = runTest {
        coEvery { accountApi.disconnectChzzk() } returns Response.success(
            ApiResponse(
                success = true,
                message = "OK",
                data = Unit,
            ),
        )

        val result = repository.disconnectChzzk()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { accountApi.disconnectChzzk() }
    }

    private fun buildAuthUrl(): String {
        return "https://chzzk.naver.com/account-interlock?" +
            "clientId=c1f78a8f-aee0-487d-ae80-36ff9e627092&" +
            "redirectUri=http%3A%2F%2F3.35.202.126%3A8080%2Fapi%2Fv1%2Fplatforms%2Fchzzk%2Fcallback&" +
            "state=test-state-123"
    }
}
