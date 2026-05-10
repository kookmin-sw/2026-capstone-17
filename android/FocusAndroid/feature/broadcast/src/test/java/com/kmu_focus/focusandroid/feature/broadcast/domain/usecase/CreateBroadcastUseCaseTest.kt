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

class CreateBroadcastUseCaseTest {

    private lateinit var repository: BroadcastRepository
    private lateinit var useCase: CreateBroadcastUseCase

    @Before
    fun setup() {
        repository = mockk()
        useCase = CreateBroadcastUseCase(repository)
    }

    @Test
    fun `방송 생성 성공 시 READY 상태의 Broadcast와 streamKey를 반환한다`() = runTest {
        val expected = Broadcast(
            broadcastId = "broadcast-1",
            title = "테스트 방송",
            memberName = "홍길동",
            memberId = "member-1",
            status = BroadcastStatus.READY,
            streamKey = "stream-key-abc",
            hlsUrl = null,
            startedAt = null,
            endedAt = null,
        )
        coEvery { repository.createBroadcast("테스트 방송") } returns Result.success(expected)

        val result = useCase("테스트 방송")

        assertTrue(result.isSuccess)
        val broadcast = result.getOrThrow()
        assertEquals("broadcast-1", broadcast.broadcastId)
        assertEquals(BroadcastStatus.READY, broadcast.status)
        assertEquals("stream-key-abc", broadcast.streamKey)
        coVerify(exactly = 1) { repository.createBroadcast("테스트 방송") }
    }

    @Test
    fun `방송 생성 실패 시 Result failure를 반환한다`() = runTest {
        coEvery { repository.createBroadcast(any()) } returns Result.failure(RuntimeException("네트워크 오류"))

        val result = useCase("테스트 방송")

        assertTrue(result.isFailure)
        assertEquals("네트워크 오류", result.exceptionOrNull()?.message)
    }

    @Test
    fun `빈 제목으로 방송 생성 시 Result failure를 반환한다`() = runTest {
        val result = useCase("")

        assertTrue(result.isFailure)
    }
}
