package com.kmu_focus.focusandroid.feature.auth.domain.usecase

import com.kmu_focus.focusandroid.feature.auth.domain.model.AuthError
import com.kmu_focus.focusandroid.feature.auth.domain.repository.KakaoAuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KakaoLoginUseCaseTest {

    private lateinit var kakaoAuthRepository: KakaoAuthRepository
    private lateinit var useCase: KakaoLoginUseCase

    @Before
    fun setup() {
        kakaoAuthRepository = mockk()
        useCase = KakaoLoginUseCase(kakaoAuthRepository)
    }

    @Test
    fun `카카오 로그인 성공 시 액세스 토큰을 반환한다`() = runTest {
        val expectedToken = "kakao_access_token_123"
        val context = mockk<Any>()
        coEvery { kakaoAuthRepository.login(context) } returns Result.success(expectedToken)

        val result = useCase(context)

        assertTrue(result.isSuccess)
        assertEquals(expectedToken, result.getOrNull())
        coVerify(exactly = 1) { kakaoAuthRepository.login(context) }
    }

    @Test
    fun `카카오 로그인 실패 시 에러를 반환한다`() = runTest {
        val context = mockk<Any>()
        val exception = AuthError.UserCancelled
        coEvery { kakaoAuthRepository.login(context) } returns Result.failure(exception)

        val result = useCase(context)

        assertTrue(result.isFailure)
        assertEquals("로그인 취소", result.exceptionOrNull()?.message)
    }

    @Test
    fun `카카오 로그인 중 예외 발생 시 AuthError로 감싼다`() = runTest {
        val context = mockk<Any>()
        coEvery { kakaoAuthRepository.login(context) } throws RuntimeException("네트워크 오류")

        val result = useCase(context)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AuthError.Unexpected)
    }
}
