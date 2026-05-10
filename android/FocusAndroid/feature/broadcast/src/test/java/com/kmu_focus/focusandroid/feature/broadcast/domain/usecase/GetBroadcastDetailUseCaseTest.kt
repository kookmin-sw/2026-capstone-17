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

class GetBroadcastDetailUseCaseTest {

    private lateinit var repository: BroadcastRepository
    private lateinit var useCase: GetBroadcastDetailUseCase

    @Before
    fun setup() {
        repository = mockk()
        useCase = GetBroadcastDetailUseCase(repository)
    }

    @Test
    fun `방송 상세 조회 성공 시 Broadcast를 반환한다`() = runTest {
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
        coEvery { repository.getBroadcastDetail("broadcast-1") } returns Result.success(expected)

        val result = useCase("broadcast-1")

        assertTrue(result.isSuccess)
        assertEquals("broadcast-1", result.getOrThrow().broadcastId)
        assertEquals("테스트 방송", result.getOrThrow().title)
        coVerify(exactly = 1) { repository.getBroadcastDetail("broadcast-1") }
    }

    @Test
    fun `존재하지 않는 방송 조회 시 Result failure를 반환한다`() = runTest {
        coEvery { repository.getBroadcastDetail(any()) } returns Result.failure(RuntimeException("방송을 찾을 수 없습니다"))

        val result = useCase("invalid-id")

        assertTrue(result.isFailure)
        assertEquals("방송을 찾을 수 없습니다", result.exceptionOrNull()?.message)
    }
}
