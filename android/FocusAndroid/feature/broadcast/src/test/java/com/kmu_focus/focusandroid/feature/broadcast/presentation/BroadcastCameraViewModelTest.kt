package com.kmu_focus.focusandroid.feature.broadcast.presentation

import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastStatus
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.BroadcastStreamingUseCase
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.StopBroadcastUseCase
import com.kmu_focus.focusandroid.feature.broadcast.presentation.camera.BroadcastCameraViewModel
import com.kmu_focus.focusandroid.core.streaming.domain.entity.SrtConnectionState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BroadcastCameraViewModelTest {

    private lateinit var broadcastStreamingUseCase: BroadcastStreamingUseCase
    private lateinit var viewModel: BroadcastCameraViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        broadcastStreamingUseCase = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `초기 상태는 미방송 상태이다`() {
        viewModel = BroadcastCameraViewModel(
            broadcastStreamingUseCase = broadcastStreamingUseCase,
            broadcastId = "broadcast-1",
            streamKey = "stream-key-abc",
        )

        val state = viewModel.uiState.value
        assertEquals("broadcast-1", state.broadcastId)
        assertEquals("stream-key-abc", state.streamKey)
        assertFalse(state.isBroadcasting)
        assertEquals(SrtConnectionState.DISCONNECTED, state.srtState)
        assertNull(state.error)
    }

    @Test
    fun `startBroadcasting 성공 시 isBroadcasting이 true가 된다`() = runTest {
        coEvery {
            broadcastStreamingUseCase.startBroadcast(any(), any(), any(), any(), any())
        } returns Result.success(Unit)
        every {
            broadcastStreamingUseCase.startHeartbeat(any(), any())
        } returns Job()

        viewModel = BroadcastCameraViewModel(
            broadcastStreamingUseCase = broadcastStreamingUseCase,
            broadcastId = "broadcast-1",
            streamKey = "stream-key-abc",
        )

        viewModel.startBroadcasting("avatar-a")

        assertTrue(viewModel.uiState.value.isBroadcasting)
    }

    @Test
    fun `startBroadcasting 실패 시 error가 설정된다`() = runTest {
        coEvery {
            broadcastStreamingUseCase.startBroadcast(any(), any(), any(), any(), any())
        } returns Result.failure(RuntimeException("SRT 연결 실패"))

        viewModel = BroadcastCameraViewModel(
            broadcastStreamingUseCase = broadcastStreamingUseCase,
            broadcastId = "broadcast-1",
            streamKey = "stream-key-abc",
        )

        viewModel.startBroadcasting("avatar-a")

        assertFalse(viewModel.uiState.value.isBroadcasting)
        assertEquals("SRT 연결 실패", viewModel.uiState.value.error)
    }

    @Test
    fun `stopBroadcasting 호출 시 isBroadcasting이 false가 된다`() = runTest {
        coEvery {
            broadcastStreamingUseCase.startBroadcast(any(), any(), any(), any(), any())
        } returns Result.success(Unit)
        every {
            broadcastStreamingUseCase.startHeartbeat(any(), any())
        } returns Job()
        coEvery {
            broadcastStreamingUseCase.stopBroadcast(any())
        } returns Result.success(Unit)

        viewModel = BroadcastCameraViewModel(
            broadcastStreamingUseCase = broadcastStreamingUseCase,
            broadcastId = "broadcast-1",
            streamKey = "stream-key-abc",
        )

        viewModel.startBroadcasting("avatar-a")
        assertTrue(viewModel.uiState.value.isBroadcasting)

        viewModel.stopBroadcasting()
        assertFalse(viewModel.uiState.value.isBroadcasting)
    }

    @Test
    fun `stopBroadcasting 호출 시 stopBroadcast UseCase가 호출된다`() = runTest {
        coEvery {
            broadcastStreamingUseCase.startBroadcast(any(), any(), any(), any(), any())
        } returns Result.success(Unit)
        every {
            broadcastStreamingUseCase.startHeartbeat(any(), any())
        } returns Job()
        coEvery {
            broadcastStreamingUseCase.stopBroadcast("broadcast-1")
        } returns Result.success(Unit)

        viewModel = BroadcastCameraViewModel(
            broadcastStreamingUseCase = broadcastStreamingUseCase,
            broadcastId = "broadcast-1",
            streamKey = "stream-key-abc",
        )

        viewModel.startBroadcasting("avatar-a")
        viewModel.stopBroadcasting()

        coVerify(exactly = 1) { broadcastStreamingUseCase.stopBroadcast("broadcast-1") }
    }

    @Test
    fun `방송 중이 아닐 때 stopBroadcasting 호출 시 무시된다`() = runTest {
        viewModel = BroadcastCameraViewModel(
            broadcastStreamingUseCase = broadcastStreamingUseCase,
            broadcastId = "broadcast-1",
            streamKey = "stream-key-abc",
        )

        viewModel.stopBroadcasting()

        coVerify(exactly = 0) { broadcastStreamingUseCase.stopBroadcast(any()) }
    }

    @Test
    fun `startBroadcasting 성공 시 하트비트가 시작된다`() = runTest {
        coEvery {
            broadcastStreamingUseCase.startBroadcast(any(), any(), any(), any(), any())
        } returns Result.success(Unit)
        every {
            broadcastStreamingUseCase.startHeartbeat(any(), any())
        } returns Job()

        viewModel = BroadcastCameraViewModel(
            broadcastStreamingUseCase = broadcastStreamingUseCase,
            broadcastId = "broadcast-1",
            streamKey = "stream-key-abc",
        )

        viewModel.startBroadcasting("avatar-a")

        every {
            broadcastStreamingUseCase.startHeartbeat(eq("broadcast-1"), any())
        }
    }

    @Test
    fun `error 발생 후 다시 시작하면 error가 초기화된다`() = runTest {
        coEvery {
            broadcastStreamingUseCase.startBroadcast(any(), any(), any(), any(), any())
        } returns Result.failure(RuntimeException("실패")) andThen Result.success(Unit)
        every {
            broadcastStreamingUseCase.startHeartbeat(any(), any())
        } returns Job()

        viewModel = BroadcastCameraViewModel(
            broadcastStreamingUseCase = broadcastStreamingUseCase,
            broadcastId = "broadcast-1",
            streamKey = "stream-key-abc",
        )

        viewModel.startBroadcasting("avatar-a")
        assertEquals("실패", viewModel.uiState.value.error)

        viewModel.startBroadcasting("avatar-a")
        assertNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.isBroadcasting)
    }
}
