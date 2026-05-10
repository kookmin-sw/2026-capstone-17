package com.kmu_focus.focusandroid.feature.auth.data.repository

import com.kmu_focus.focusandroid.core.network.data.TokenRefreshService
import com.kmu_focus.focusandroid.core.network.domain.TokenStore
import com.kmu_focus.focusandroid.core.network.dto.ApiResponse
import com.kmu_focus.focusandroid.core.network.dto.AppTokenResponse
import com.kmu_focus.focusandroid.feature.auth.data.remote.AuthApi
import com.kmu_focus.focusandroid.feature.auth.data.remote.dto.KakaoLoginRequest
import com.kmu_focus.focusandroid.feature.auth.domain.model.AuthError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.util.Base64

class ServerAuthRepositoryImplTest {

    private lateinit var authApi: AuthApi
    private lateinit var tokenStore: TokenStore
    private lateinit var tokenRefreshService: TokenRefreshService
    private lateinit var repository: ServerAuthRepositoryImpl

    @Before
    fun setup() {
        authApi = mockk()
        tokenStore = mockk(relaxed = true)
        tokenRefreshService = mockk()
        repository = ServerAuthRepositoryImpl(authApi, tokenStore, tokenRefreshService)
    }

    @Test
    fun `로그인 성공 시 토큰을 저장하고 성공을 반환한다`() = runTest {
        val tokenResponse = AppTokenResponse("server_access", "server_refresh")
        val apiResponse = ApiResponse(success = true, message = "OK", data = tokenResponse)
        coEvery { authApi.kakaoLogin(any()) } returns Response.success(apiResponse)

        val result = repository.loginWithKakaoToken("kakao_token")

        assertTrue(result.isSuccess)
        coVerify { authApi.kakaoLogin(KakaoLoginRequest(accessToken = "kakao_token")) }
        coVerify { tokenStore.save("server_access", "server_refresh") }
    }

    @Test
    fun `서버가 success false를 반환하면 실패한다`() = runTest {
        val apiResponse = ApiResponse<AppTokenResponse>(
            success = false,
            message = "외부 서버와 통신 과정 중 에러가 발생했습니다.",
            data = null,
        )
        coEvery { authApi.kakaoLogin(any()) } returns Response.success(apiResponse)

        val result = repository.loginWithKakaoToken("kakao_token")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AuthError.Network)
    }

    @Test
    fun `서버가 400을 반환하면 실패한다`() = runTest {
        coEvery { authApi.kakaoLogin(any()) } returns Response.error(
            400,
            """{"success":false,"message":"잘못된 요청","errorTitle":"InvalidInputValue","errorCode":400}"""
                .toResponseBody(),
        )

        val result = repository.loginWithKakaoToken("kakao_token")

        assertTrue(result.isFailure)
    }

    @Test
    fun `서버가 500을 반환하면 실패한다`() = runTest {
        coEvery { authApi.kakaoLogin(any()) } returns Response.error(
            500,
            "Internal Server Error".toResponseBody(),
        )

        val result = repository.loginWithKakaoToken("kakao_token")

        assertTrue(result.isFailure)
    }

    @Test
    fun `네트워크 예외 발생 시 실패한다`() = runTest {
        coEvery { authApi.kakaoLogin(any()) } throws java.io.IOException("네트워크 끊김")

        val result = repository.loginWithKakaoToken("kakao_token")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AuthError.Network)
    }

    @Test
    fun `응답 data가 null이면 실패한다`() = runTest {
        val apiResponse = ApiResponse<AppTokenResponse>(
            success = true,
            message = "OK",
            data = null,
        )
        coEvery { authApi.kakaoLogin(any()) } returns Response.success(apiResponse)

        val result = repository.loginWithKakaoToken("kakao_token")

        assertTrue(result.isFailure)
    }

    @Test
    fun `유효한 access token이 있으면 자동 로그인 성공을 반환한다`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns createJwt(expEpochSeconds = futureEpochSeconds())
        coEvery { tokenStore.getRefreshToken() } returns "refresh_token"

        val result = repository.validateStoredSession()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
        coVerify(exactly = 0) { tokenRefreshService.refresh() }
    }

    @Test
    fun `만료된 access token과 refresh token이 있으면 refresh 후 성공을 반환한다`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns createJwt(expEpochSeconds = pastEpochSeconds())
        coEvery { tokenStore.getRefreshToken() } returns "refresh_token"
        coEvery { tokenRefreshService.refresh() } returns true

        val result = repository.validateStoredSession()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { tokenRefreshService.refresh() }
    }

    @Test
    fun `저장된 토큰이 없으면 TokenMissing 실패를 반환한다`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns null
        coEvery { tokenStore.getRefreshToken() } returns null

        val result = repository.validateStoredSession()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AuthError.TokenMissing)
    }

    @Test
    fun `refresh token이 있지만 refresh 실패 시 TokenExpired 실패를 반환한다`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns createJwt(expEpochSeconds = pastEpochSeconds())
        coEvery { tokenStore.getRefreshToken() } returns "refresh_token"
        coEvery { tokenRefreshService.refresh() } returns false

        val result = repository.validateStoredSession()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AuthError.TokenExpired)
    }

    @Test
    fun `refresh 중 네트워크 예외가 발생하면 Network 실패를 반환한다`() = runTest {
        coEvery { tokenStore.getAccessToken() } returns createJwt(expEpochSeconds = pastEpochSeconds())
        coEvery { tokenStore.getRefreshToken() } returns "refresh_token"
        coEvery { tokenRefreshService.refresh() } throws java.io.IOException("네트워크 끊김")

        val result = repository.validateStoredSession()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AuthError.Network)
    }

    private fun createJwt(expEpochSeconds: Long): String {
        val header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"exp":$expEpochSeconds}""".toByteArray())
        return "$header.$payload.signature"
    }

    private fun futureEpochSeconds(): Long = System.currentTimeMillis() / 1000 + 3_600

    private fun pastEpochSeconds(): Long = System.currentTimeMillis() / 1000 - 3_600
}
