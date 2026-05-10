package com.kmu_focus.focusandroid.feature.broadcast.domain.usecase

import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastStatus
import com.kmu_focus.focusandroid.feature.broadcast.domain.repository.BroadcastRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StopBroadcastUseCaseTest {

    private lateinit var repository: BroadcastRepository
    private lateinit var useCase: StopBroadcastUseCase

    @Before
    fun setup() {
        repository = mockk()
        useCase = StopBroadcastUseCase(repository)
    }

    @Test
    fun `방송 종료 성공 시 ENDED 상태를 반환한다`() = runTest {
        val expected = Broadcast(
            broadcastId = "broadcast-1",
            title = "테스트 방송",
            memberName = "홍길동",
            memberId = "member-1",
            status = BroadcastStatus.ENDED,
            streamKey = "stream-key-abc",
            hlsUrl = "https://cdn.example.com/live/broadcast-1.m3u8",
            startedAt = "2026-04-09T12:00:00",
            endedAt = "2026-04-09T13:00:00",
        )
        coEvery { repository.stopBroadcast("broadcast-1") } returns Result.success(expected)

        val result = useCase("broadcast-1")

        assertTrue(result.isSuccess)
        assertEquals(BroadcastStatus.ENDED, result.getOrThrow().status)
        coVerify(exactly = 1) { repository.stopBroadcast("broadcast-1") }
    }

    @Test
    fun `방송 종료 실패 시 Result failure를 반환한다`() = runTest {
        coEvery { repository.stopBroadcast(any()) } returns Result.failure(RuntimeException("서버 오류"))

        val result = useCase("broadcast-1")

        assertTrue(result.isFailure)
    }
}
