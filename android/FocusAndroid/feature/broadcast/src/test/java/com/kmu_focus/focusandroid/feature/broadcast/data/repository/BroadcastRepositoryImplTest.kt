package com.kmu_focus.focusandroid.feature.broadcast.data.repository

import com.kmu_focus.focusandroid.core.network.dto.ApiResponse
import com.kmu_focus.focusandroid.feature.broadcast.data.remote.BroadcastApi
import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.BroadcastResponseDto
import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.CreateBroadcastRequestDto
import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.PageBroadcastResponseDto
import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.StartBroadcastRequestDto
import com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto.UpdateBroadcastRequestDto
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class BroadcastRepositoryImplTest {

    private lateinit var api: BroadcastApi
    private lateinit var repository: BroadcastRepositoryImpl

    private val sampleDto = BroadcastResponseDto(
        broadcastId = "broadcast-1",
        title = "테스트 방송",
        memberName = "홍길동",
        memberId = "member-1",
        status = "READY",
        streamKey = "stream-key-abc",
        hlsUrl = null,
        startedAt = null,
        endedAt = null,
    )

    @Before
    fun setup() {
        api = mockk()
        repository = BroadcastRepositoryImpl(api)
    }

    @Test
    fun `createBroadcast 성공 시 DTO를 Entity로 매핑하여 반환한다`() = runTest {
        coEvery { api.createBroadcast(any()) } returns Response.success(
            201,
            ApiResponse(success = true, message = "성공", data = sampleDto),
        )

        val result = repository.createBroadcast("테스트 방송")

        assertTrue(result.isSuccess)
        val broadcast = result.getOrThrow()
        assertEquals("broadcast-1", broadcast.broadcastId)
        assertEquals(BroadcastStatus.READY, broadcast.status)
        assertEquals("stream-key-abc", broadcast.streamKey)
    }

    @Test
    fun `createBroadcast 서버 에러 시 Result failure를 반환한다`() = runTest {
        coEvery { api.createBroadcast(any()) } returns Response.success(
            ApiResponse(success = false, message = "서버 오류", data = null),
        )

        val result = repository.createBroadcast("테스트 방송")

        assertTrue(result.isFailure)
    }

    @Test
    fun `startBroadcast 성공 시 ON_AIR 상태와 hlsUrl을 매핑한다`() = runTest {
        val onAirDto = sampleDto.copy(
            status = "ON_AIR",
            hlsUrl = "https://cdn.example.com/live/broadcast-1.m3u8",
            startedAt = "2026-04-09T12:00:00",
        )
        coEvery { api.startBroadcast(any(), any()) } returns Response.success(
            ApiResponse(success = true, message = "성공", data = onAirDto),
        )

        val result = repository.startBroadcast("broadcast-1", "avatar-a")

        assertTrue(result.isSuccess)
        assertEquals(BroadcastStatus.ON_AIR, result.getOrThrow().status)
        assertEquals("https://cdn.example.com/live/broadcast-1.m3u8", result.getOrThrow().hlsUrl)
    }

    @Test
    fun `stopBroadcast 성공 시 ENDED 상태를 매핑한다`() = runTest {
        val endedDto = sampleDto.copy(
            status = "ENDED",
            endedAt = "2026-04-09T13:00:00",
        )
        coEvery { api.stopBroadcast(any()) } returns Response.success(
            ApiResponse(success = true, message = "성공", data = endedDto),
        )

        val result = repository.stopBroadcast("broadcast-1")

        assertTrue(result.isSuccess)
        assertEquals(BroadcastStatus.ENDED, result.getOrThrow().status)
    }

    @Test
    fun `getBroadcastList 성공 시 Broadcast 리스트를 반환한다`() = runTest {
        val pageDto = PageBroadcastResponseDto(
            content = listOf(sampleDto, sampleDto.copy(broadcastId = "broadcast-2")),
            totalElements = 2,
            totalPages = 1,
            size = 10,
            number = 0,
            first = true,
            last = true,
            empty = false,
        )
        coEvery { api.getBroadcastList(any(), any()) } returns Response.success(
            ApiResponse(success = true, message = "성공", data = pageDto),
        )

        val result = repository.getBroadcastList(page = 0, size = 10)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
    }

    @Test
    fun `getBroadcastDetail 성공 시 Broadcast를 반환한다`() = runTest {
        coEvery { api.getBroadcastDetail(any()) } returns Response.success(
            ApiResponse(success = true, message = "성공", data = sampleDto),
        )

        val result = repository.getBroadcastDetail("broadcast-1")

        assertTrue(result.isSuccess)
        assertEquals("broadcast-1", result.getOrThrow().broadcastId)
    }

    @Test
    fun `updateBroadcast 성공 시 수정된 Broadcast를 반환한다`() = runTest {
        val updatedDto = sampleDto.copy(title = "수정된 제목")
        coEvery { api.updateBroadcast(any(), any()) } returns Response.success(
            ApiResponse(success = true, message = "성공", data = updatedDto),
        )

        val result = repository.updateBroadcast("broadcast-1", "수정된 제목")

        assertTrue(result.isSuccess)
        assertEquals("수정된 제목", result.getOrThrow().title)
    }

    @Test
    fun `deleteBroadcast 성공 시 Result success를 반환한다`() = runTest {
        coEvery { api.deleteBroadcast(any()) } returns Response.success(
            ApiResponse(success = true, message = "성공", data = null),
        )

        val result = repository.deleteBroadcast("broadcast-1")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `sendStreamerHeartbeat 성공 시 Result success를 반환한다`() = runTest {
        coEvery { api.streamerHeartbeat(any()) } returns Response.success(
            ApiResponse(success = true, message = "성공", data = null),
        )

        val result = repository.sendStreamerHeartbeat("broadcast-1")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `네트워크 예외 발생 시 Result failure를 반환한다`() = runTest {
        coEvery { api.createBroadcast(any()) } throws RuntimeException("네트워크 연결 실패")

        val result = repository.createBroadcast("테스트 방송")

        assertTrue(result.isFailure)
        assertEquals("네트워크 연결 실패", result.exceptionOrNull()?.message)
    }
}
