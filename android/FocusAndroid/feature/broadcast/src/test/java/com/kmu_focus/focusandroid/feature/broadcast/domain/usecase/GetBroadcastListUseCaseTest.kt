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

class GetBroadcastListUseCaseTest {

    private lateinit var repository: BroadcastRepository
    private lateinit var useCase: GetBroadcastListUseCase

    @Before
    fun setup() {
        repository = mockk()
        useCase = GetBroadcastListUseCase(repository)
    }

    @Test
    fun `방송 목록 조회 성공 시 Broadcast 리스트를 반환한다`() = runTest {
        val broadcasts = listOf(
            Broadcast(
                broadcastId = "broadcast-1",
                title = "방송 1",
                memberName = "홍길동",
                memberId = "member-1",
                status = BroadcastStatus.ON_AIR,
                streamKey = "key-1",
                hlsUrl = "https://cdn.example.com/1.m3u8",
                startedAt = "2026-04-09T12:00:00",
                endedAt = null,
            ),
            Broadcast(
                broadcastId = "broadcast-2",
                title = "방송 2",
                memberName = "김철수",
                memberId = "member-2",
                status = BroadcastStatus.READY,
                streamKey = "key-2",
                hlsUrl = null,
                startedAt = null,
                endedAt = null,
            ),
        )
        coEvery { repository.getBroadcastList(page = 0, size = 10) } returns Result.success(broadcasts)

        val result = useCase(page = 0, size = 10)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
        coVerify(exactly = 1) { repository.getBroadcastList(page = 0, size = 10) }
    }

    @Test
    fun `방송 목록이 비어있을 때 빈 리스트를 반환한다`() = runTest {
        coEvery { repository.getBroadcastList(any(), any()) } returns Result.success(emptyList())

        val result = useCase(page = 0, size = 10)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `방송 목록 조회 실패 시 Result failure를 반환한다`() = runTest {
        coEvery { repository.getBroadcastList(any(), any()) } returns Result.failure(RuntimeException("네트워크 오류"))

        val result = useCase(page = 0, size = 10)

        assertTrue(result.isFailure)
    }
}
