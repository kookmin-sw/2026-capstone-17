package com.kmu_focus.focusandroid.feature.auth.domain.usecase

import com.kmu_focus.focusandroid.feature.auth.domain.model.AuthError
import com.kmu_focus.focusandroid.feature.auth.domain.repository.ServerAuthRepository
import com.kmu_focus.focusandroid.feature.auth.domain.session.AuthSessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutoLoginUseCaseTest {

    private lateinit var serverAuthRepository: ServerAuthRepository
    private lateinit var authSessionManager: AuthSessionManager
    private lateinit var useCase: AutoLoginUseCase

    @Before
    fun setup() {
        serverAuthRepository = mockk()
        authSessionManager = AuthSessionManager()
        useCase = AutoLoginUseCase(serverAuthRepository, authSessionManager)
    }

    @Test
    fun `저장된 서버 토큰이 유효하면 true를 반환한다`() = runTest {
        coEvery { serverAuthRepository.validateStoredSession() } returns Result.success(true)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
        assertTrue(authSessionManager.isLoggedIn.value)
        coVerify(exactly = 1) { serverAuthRepository.validateStoredSession() }
    }

    @Test
    fun `서버 토큰이 만료되면 실패를 반환하고 세션이 비활성화된다`() = runTest {
        coEvery { serverAuthRepository.validateStoredSession() } returns Result.failure(AuthError.TokenExpired)

        val result = useCase()

        assertTrue(result.isFailure)
        assertFalse(authSessionManager.isLoggedIn.value)
    }

    @Test
    fun `토큰이 없으면 실패를 반환한다`() = runTest {
        coEvery { serverAuthRepository.validateStoredSession() } returns Result.failure(
            AuthError.TokenMissing
        )

        val result = useCase()

        assertTrue(result.isFailure)
        assertFalse(authSessionManager.isLoggedIn.value)
    }

    @Test
    fun `토큰 검증 중 네트워크 오류 시 실패를 반환한다`() = runTest {
        coEvery { serverAuthRepository.validateStoredSession() } returns Result.failure(
            AuthError.Network("네트워크 오류")
        )

        val result = useCase()

        assertTrue(result.isFailure)
        assertFalse(authSessionManager.isLoggedIn.value)
    }
}
