package com.kmu_focus.focusandroid.feature.auth.domain.usecase

import com.kmu_focus.focusandroid.feature.auth.domain.model.AuthError
import com.kmu_focus.focusandroid.feature.auth.domain.repository.ServerAuthRepository
import com.kmu_focus.focusandroid.feature.auth.domain.session.AuthSessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServerLoginUseCaseTest {

    private lateinit var serverAuthRepository: ServerAuthRepository
    private lateinit var authSessionManager: AuthSessionManager
    private lateinit var useCase: ServerLoginUseCase

    @Before
    fun setup() {
        serverAuthRepository = mockk()
        authSessionManager = AuthSessionManager()
        useCase = ServerLoginUseCase(serverAuthRepository, authSessionManager)
    }

    @Test
    fun `서버 로그인 성공 시 세션이 활성화되고 성공을 반환한다`() = runTest {
        coEvery { serverAuthRepository.loginWithKakaoToken("kakao_token") } returns Result.success(Unit)

        val result = useCase("kakao_token")

        assertTrue(result.isSuccess)
        assertTrue(authSessionManager.isLoggedIn.value)
        coVerify(exactly = 1) { serverAuthRepository.loginWithKakaoToken("kakao_token") }
    }

    @Test
    fun `서버 로그인 실패 시 세션이 비활성 상태이고 실패를 반환한다`() = runTest {
        coEvery {
            serverAuthRepository.loginWithKakaoToken("kakao_token")
        } returns Result.failure(AuthError.Network("서버 오류"))

        val result = useCase("kakao_token")

        assertTrue(result.isFailure)
        assertFalse(authSessionManager.isLoggedIn.value)
    }

    @Test
    fun `서버 로그인 중 예외 발생 시 AuthError로 감싸서 반환한다`() = runTest {
        coEvery {
            serverAuthRepository.loginWithKakaoToken("kakao_token")
        } throws RuntimeException("연결 실패")

        val result = useCase("kakao_token")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AuthError.Unexpected)
        assertFalse(authSessionManager.isLoggedIn.value)
    }

    @Test
    fun `빈 토큰으로 호출 시 실패를 반환한다`() = runTest {
        val result = useCase("")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AuthError.InvalidContext)
        assertFalse(authSessionManager.isLoggedIn.value)
    }
}
