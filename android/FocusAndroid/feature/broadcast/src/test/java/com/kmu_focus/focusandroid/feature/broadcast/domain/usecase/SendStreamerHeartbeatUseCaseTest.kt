package com.kmu_focus.focusandroid.feature.broadcast.domain.usecase

import com.kmu_focus.focusandroid.feature.broadcast.domain.repository.BroadcastRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SendStreamerHeartbeatUseCaseTest {

    private lateinit var repository: BroadcastRepository
    private lateinit var useCase: SendStreamerHeartbeatUseCase

    @Before
    fun setup() {
        repository = mockk()
        useCase = SendStreamerHeartbeatUseCase(repository)
    }

    @Test
    fun `하트비트 전송 성공 시 Result success를 반환한다`() = runTest {
        coEvery { repository.sendStreamerHeartbeat("broadcast-1") } returns Result.success(Unit)

        val result = useCase("broadcast-1")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.sendStreamerHeartbeat("broadcast-1") }
    }

    @Test
    fun `하트비트 전송 실패 시 Result failure를 반환한다`() = runTest {
        coEvery { repository.sendStreamerHeartbeat(any()) } returns Result.failure(RuntimeException("연결 끊김"))

        val result = useCase("broadcast-1")

        assertTrue(result.isFailure)
    }
}
