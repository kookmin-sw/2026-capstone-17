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

class StartBroadcastUseCaseTest {

    private lateinit var repository: BroadcastRepository
    private lateinit var useCase: StartBroadcastUseCase

    @Before
    fun setup() {
        repository = mockk()
        useCase = StartBroadcastUseCase(repository)
    }

    @Test
    fun `방송 시작 성공 시 ON_AIR 상태와 hlsUrl을 반환한다`() = runTest {
        val expected = Broadcast(
            broadcastId = "broadcast-1",
            title = "테스트 방송",
            memberName = "홍길동",
            memberId = "member-1",
            status = BroadcastStatus.ON_AIR,
            streamKey = "stream-key-abc",
            hlsUrl = "https://cdn.example.com/live/broadcast-1.m3u8",
            startedAt = "2026-04-09T12:00:00",
            endedAt = null,
        )
        coEvery { repository.startBroadcast("broadcast-1", "avatar-a") } returns Result.success(expected)

        val result = useCase(broadcastId = "broadcast-1", avatarId = "avatar-a")

        assertTrue(result.isSuccess)
        val broadcast = result.getOrThrow()
        assertEquals(BroadcastStatus.ON_AIR, broadcast.status)
        assertEquals("https://cdn.example.com/live/broadcast-1.m3u8", broadcast.hlsUrl)
        coVerify(exactly = 1) { repository.startBroadcast("broadcast-1", "avatar-a") }
    }

    @Test
    fun `방송 시작 실패 시 Result failure를 반환한다`() = runTest {
        coEvery { repository.startBroadcast(any(), any()) } returns Result.failure(RuntimeException("워커 시작 실패"))

        val result = useCase(broadcastId = "broadcast-1", avatarId = "avatar-a")

        assertTrue(result.isFailure)
        assertEquals("워커 시작 실패", result.exceptionOrNull()?.message)
    }
}
