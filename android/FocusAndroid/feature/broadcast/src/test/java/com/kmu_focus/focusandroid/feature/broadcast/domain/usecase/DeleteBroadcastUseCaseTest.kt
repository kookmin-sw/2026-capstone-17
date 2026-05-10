package com.kmu_focus.focusandroid.feature.broadcast.domain.usecase

import com.kmu_focus.focusandroid.feature.broadcast.domain.repository.BroadcastRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeleteBroadcastUseCaseTest {

    private lateinit var repository: BroadcastRepository
    private lateinit var useCase: DeleteBroadcastUseCase

    @Before
    fun setup() {
        repository = mockk()
        useCase = DeleteBroadcastUseCase(repository)
    }

    @Test
    fun `방송 삭제 성공 시 Result success를 반환한다`() = runTest {
        coEvery { repository.deleteBroadcast("broadcast-1") } returns Result.success(Unit)

        val result = useCase("broadcast-1")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.deleteBroadcast("broadcast-1") }
    }

    @Test
    fun `방송 삭제 실패 시 Result failure를 반환한다`() = runTest {
        coEvery { repository.deleteBroadcast(any()) } returns Result.failure(RuntimeException("권한 없음"))

        val result = useCase("broadcast-1")

        assertTrue(result.isFailure)
    }
}
