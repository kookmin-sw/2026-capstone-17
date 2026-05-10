package com.kmu_focus.focusandroid.feature.broadcast.domain.usecase

import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastStatus
import com.kmu_focus.focusandroid.feature.broadcast.domain.repository.BroadcastRepository
import com.kmu_focus.focusandroid.core.streaming.domain.entity.SrtConnectionConfig
import com.kmu_focus.focusandroid.core.streaming.domain.repository.SrtStreamRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BroadcastStreamingUseCaseTest {

    private lateinit var srtStreamRepository: SrtStreamRepository
    private lateinit var broadcastRepository: BroadcastRepository
    private lateinit var useCase: BroadcastStreamingUseCase

    private val startedBroadcast = Broadcast(
        broadcastId = "broadcast-1",
        title = "테스트 방송",
        memberName = "홍길동",
        memberId = "member-1",
        status = BroadcastStatus.ON_AIR,
        streamKey = "stream-key-abc",
        hlsUrl = "https://cdn.example.com/hls/broadcast-1/index.m3u8",
        startedAt = "2026-04-09T12:00:00",
        endedAt = null,
    )

    @Before
    fun setup() {
        srtStreamRepository = mockk(relaxed = true)
        broadcastRepository = mockk(relaxed = true)
        useCase = BroadcastStreamingUseCase(srtStreamRepository, broadcastRepository)
    }

    @Test
    fun `startBroadcast 호출 시 SRT MuxerFactory를 생성한다`() = runTest {
        every { srtStreamRepository.createMuxerFactory(any()) } returns mockk()
        coEvery { broadcastRepository.startBroadcast(any(), any()) } returns Result.success(startedBroadcast)

        val result = useCase.startBroadcast(
            broadcastId = "broadcast-1",
            streamKey = "stream-key-abc",
            avatarId = "avatar-a",
            mediaMtxHost = "13.125.126.120",
            mediaMtxPort = 8890,
        )

        assertTrue(result.isSuccess)
        every {
            srtStreamRepository.createMuxerFactory(
                match { it.streamKey == "stream-key-abc" && it.host == "13.125.126.120" },
            )
        }
    }

    @Test
    fun `startBroadcast 성공 시 서버에 방송 시작 API를 호출한다`() = runTest {
        every { srtStreamRepository.createMuxerFactory(any()) } returns mockk()
        coEvery { broadcastRepository.startBroadcast("broadcast-1", "avatar-a") } returns Result.success(startedBroadcast)

        useCase.startBroadcast(
            broadcastId = "broadcast-1",
            streamKey = "stream-key-abc",
            avatarId = "avatar-a",
            mediaMtxHost = "13.125.126.120",
            mediaMtxPort = 8890,
        )

        coVerify(exactly = 1) { broadcastRepository.startBroadcast("broadcast-1", "avatar-a") }
    }

    @Test
    fun `startBroadcast에서 서버 API 실패 시 Result failure를 반환한다`() = runTest {
        every { srtStreamRepository.createMuxerFactory(any()) } returns mockk()
        coEvery { broadcastRepository.startBroadcast(any(), any()) } returns Result.failure(RuntimeException("워커 시작 실패"))

        val result = useCase.startBroadcast(
            broadcastId = "broadcast-1",
            streamKey = "stream-key-abc",
            avatarId = "avatar-a",
            mediaMtxHost = "13.125.126.120",
            mediaMtxPort = 8890,
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `stopBroadcast 호출 시 SRT 연결을 해제한다`() = runTest {
        coEvery { srtStreamRepository.disconnect() } returns Unit
        coEvery { broadcastRepository.stopBroadcast("broadcast-1") } returns Result.success(
            startedBroadcast.copy(status = BroadcastStatus.ENDED),
        )

        val result = useCase.stopBroadcast("broadcast-1")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { srtStreamRepository.disconnect() }
    }

    @Test
    fun `stopBroadcast 호출 시 서버에 방송 종료 API를 호출한다`() = runTest {
        coEvery { srtStreamRepository.disconnect() } returns Unit
        coEvery { broadcastRepository.stopBroadcast("broadcast-1") } returns Result.success(
            startedBroadcast.copy(status = BroadcastStatus.ENDED),
        )

        useCase.stopBroadcast("broadcast-1")

        coVerify(exactly = 1) { broadcastRepository.stopBroadcast("broadcast-1") }
    }

    @Test
    fun `startHeartbeat는 10초 간격으로 하트비트를 전송한다`() = runTest {
        coEvery { broadcastRepository.sendStreamerHeartbeat("broadcast-1") } returns Result.success(Unit)

        val job = useCase.startHeartbeat("broadcast-1", this)

        advanceTimeBy(25_000) // 25초 → 2회 전송 (10초, 20초)
        job.cancel()

        coVerify(atLeast = 2) { broadcastRepository.sendStreamerHeartbeat("broadcast-1") }
    }

    @Test
    fun `하트비트 3회 연속 실패 시 자동 중지 결과를 반환한다`() = runTest {
        coEvery { broadcastRepository.sendStreamerHeartbeat(any()) } returns Result.failure(RuntimeException("연결 끊김"))

        val job = useCase.startHeartbeat("broadcast-1", this)

        advanceTimeBy(35_000) // 3회 이상 실패하도록
        // 3회 연속 실패 시 job이 자동 취소되어야 함
        assertTrue(job.isCancelled || job.isCompleted)
    }
}
