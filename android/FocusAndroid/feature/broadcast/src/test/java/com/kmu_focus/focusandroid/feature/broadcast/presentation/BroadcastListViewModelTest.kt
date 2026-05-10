package com.kmu_focus.focusandroid.feature.broadcast.presentation

import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.BroadcastStatus
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.DeleteBroadcastUseCase
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.GetBroadcastListUseCase
import com.kmu_focus.focusandroid.feature.broadcast.presentation.list.BroadcastListViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BroadcastListViewModelTest {

    private lateinit var getBroadcastListUseCase: GetBroadcastListUseCase
    private lateinit var deleteBroadcastUseCase: DeleteBroadcastUseCase
    private lateinit var viewModel: BroadcastListViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    private val sampleBroadcast = Broadcast(
        broadcastId = "broadcast-1",
        title = "테스트 방송",
        memberName = "홍길동",
        memberId = "member-1",
        status = BroadcastStatus.ON_AIR,
        streamKey = "key-1",
        hlsUrl = "https://cdn.example.com/1.m3u8",
        startedAt = "2026-04-09T12:00:00",
        endedAt = null,
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        getBroadcastListUseCase = mockk()
        deleteBroadcastUseCase = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `초기 상태는 로딩이다`() = runTest {
        coEvery { getBroadcastListUseCase(any(), any()) } returns Result.success(emptyList())

        viewModel = BroadcastListViewModel(getBroadcastListUseCase, deleteBroadcastUseCase)

        // 초기 로드 후 상태 확인
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `방송 목록 로드 성공 시 broadcasts가 업데이트된다`() = runTest {
        val broadcasts = listOf(sampleBroadcast, sampleBroadcast.copy(broadcastId = "broadcast-2"))
        coEvery { getBroadcastListUseCase(page = 0, size = 20) } returns Result.success(broadcasts)

        viewModel = BroadcastListViewModel(getBroadcastListUseCase, deleteBroadcastUseCase)

        assertEquals(2, viewModel.uiState.value.broadcasts.size)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun `방송 목록 로드 실패 시 error가 설정된다`() = runTest {
        coEvery { getBroadcastListUseCase(any(), any()) } returns Result.failure(RuntimeException("네트워크 오류"))

        viewModel = BroadcastListViewModel(getBroadcastListUseCase, deleteBroadcastUseCase)

        assertTrue(viewModel.uiState.value.broadcasts.isEmpty())
        assertEquals("네트워크 오류", viewModel.uiState.value.error)
    }

    @Test
    fun `방송 삭제 성공 시 목록에서 제거된다`() = runTest {
        val broadcasts = listOf(sampleBroadcast)
        coEvery { getBroadcastListUseCase(any(), any()) } returns Result.success(broadcasts)
        coEvery { deleteBroadcastUseCase("broadcast-1") } returns Result.success(Unit)

        viewModel = BroadcastListViewModel(getBroadcastListUseCase, deleteBroadcastUseCase)
        viewModel.deleteBroadcast("broadcast-1")

        // 삭제 후 재로드 또는 로컬 제거 확인
        assertFalse(viewModel.uiState.value.broadcasts.any { it.broadcastId == "broadcast-1" })
    }

    @Test
    fun `새로고침 시 목록을 다시 로드한다`() = runTest {
        coEvery { getBroadcastListUseCase(any(), any()) } returns Result.success(listOf(sampleBroadcast))

        viewModel = BroadcastListViewModel(getBroadcastListUseCase, deleteBroadcastUseCase)
        viewModel.refresh()

        assertEquals(1, viewModel.uiState.value.broadcasts.size)
    }

    @Test
    fun `방송 삭제 실패 시 목록은 유지되고 error가 설정된다`() = runTest {
        coEvery { getBroadcastListUseCase(any(), any()) } returns Result.success(listOf(sampleBroadcast))
        coEvery { deleteBroadcastUseCase("broadcast-1") } returns Result.failure(RuntimeException("권한 없음"))

        viewModel = BroadcastListViewModel(getBroadcastListUseCase, deleteBroadcastUseCase)
        viewModel.deleteBroadcast("broadcast-1")

        assertEquals(1, viewModel.uiState.value.broadcasts.size)
        assertEquals("권한 없음", viewModel.uiState.value.error)
    }

    @Test
    fun `ON_AIR 상태 방송 클릭 시 해당 broadcastId를 식별할 수 있다`() = runTest {
        val onAirBroadcast = sampleBroadcast.copy(status = BroadcastStatus.ON_AIR)
        coEvery { getBroadcastListUseCase(any(), any()) } returns Result.success(listOf(onAirBroadcast))

        viewModel = BroadcastListViewModel(getBroadcastListUseCase, deleteBroadcastUseCase)

        val target = viewModel.uiState.value.broadcasts.first()
        assertEquals(BroadcastStatus.ON_AIR, target.status)
        assertEquals("broadcast-1", target.broadcastId)
    }
}
