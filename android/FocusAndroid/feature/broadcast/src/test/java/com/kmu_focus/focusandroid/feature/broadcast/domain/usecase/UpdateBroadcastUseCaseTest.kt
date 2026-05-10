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

class UpdateBroadcastUseCaseTest {

    private lateinit var repository: BroadcastRepository
    private lateinit var useCase: UpdateBroadcastUseCase

    @Before
    fun setup() {
        repository = mockk()
        useCase = UpdateBroadcastUseCase(repository)
    }

    @Test
    fun `방송 제목 수정 성공 시 수정된 Broadcast를 반환한다`() = runTest {
        val expected = Broadcast(
            broadcastId = "broadcast-1",
            title = "수정된 제목",
            memberName = "홍길동",
            memberId = "member-1",
            status = BroadcastStatus.READY,
            streamKey = "stream-key-abc",
            hlsUrl = null,
            startedAt = null,
            endedAt = null,
        )
        coEvery { repository.updateBroadcast("broadcast-1", "수정된 제목") } returns Result.success(expected)

        val result = useCase(broadcastId = "broadcast-1", title = "수정된 제목")

        assertTrue(result.isSuccess)
        assertEquals("수정된 제목", result.getOrThrow().title)
        coVerify(exactly = 1) { repository.updateBroadcast("broadcast-1", "수정된 제목") }
    }

    @Test
    fun `방송 수정 실패 시 Result failure를 반환한다`() = runTest {
        coEvery { repository.updateBroadcast(any(), any()) } returns Result.failure(RuntimeException("권한 없음"))

        val result = useCase(broadcastId = "broadcast-1", title = "수정된 제목")

        assertTrue(result.isFailure)
    }
}
