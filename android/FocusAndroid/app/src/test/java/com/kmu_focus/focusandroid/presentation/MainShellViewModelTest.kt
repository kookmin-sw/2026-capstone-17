package com.kmu_focus.focusandroid.presentation

import com.kmu_focus.focusandroid.core.ui.insets.FocusContentInsetMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MainShellViewModelTest {

    private lateinit var viewModel: MainShellViewModel

    @Before
    fun setup() {
        viewModel = MainShellViewModel()
    }

    @Test
    fun `초기 상태는 방송 탭이며 bottom bar가 보인다`() {
        val state = viewModel.uiState.value

        assertEquals(MainTab.BROADCAST, state.selectedTab)
        assertEquals(
            MainShellDestination.Tab(MainTab.BROADCAST),
            state.currentDestination,
        )
        assertTrue(state.isBottomBarVisible)
    }

    @Test
    fun `내 정보 탭 선택 시 프로필 탭으로 전환된다`() {
        viewModel.selectTab(MainTab.PROFILE)

        val state = viewModel.uiState.value

        assertEquals(MainTab.PROFILE, state.selectedTab)
        assertEquals(
            MainShellDestination.Tab(MainTab.PROFILE),
            state.currentDestination,
        )
        assertTrue(state.isBottomBarVisible)
    }

    @Test
    fun `동영상 탭 선택 시 동영상 탭으로 전환된다`() {
        viewModel.selectTab(MainTab.VIDEO)

        val state = viewModel.uiState.value

        assertEquals(MainTab.VIDEO, state.selectedTab)
        assertEquals(
            MainShellDestination.Tab(MainTab.VIDEO),
            state.currentDestination,
        )
        assertTrue(state.isBottomBarVisible)
    }

    @Test
    fun `방송 생성 화면으로 이동하면 bottom bar가 숨겨진다`() {
        viewModel.openBroadcastCreate()

        val state = viewModel.uiState.value

        assertEquals(MainTab.BROADCAST, state.selectedTab)
        assertEquals(MainShellDestination.BroadcastCreate, state.currentDestination)
        assertEquals(FocusContentInsetMode.ScaffoldPadding, state.currentDestination.contentInsetMode)
        assertTrue(!state.isBottomBarVisible)
    }

    @Test
    fun `방송 카메라 종료 시 방송 탭으로 복귀한다`() {
        viewModel.openBroadcastCamera(
            broadcastId = "broadcast-1",
            streamKey = "stream-key-1",
            hlsUrl = "https://cdn.example.com/live.m3u8",
        )

        viewModel.closeOverlay()

        val state = viewModel.uiState.value

        assertEquals(MainTab.BROADCAST, state.selectedTab)
        assertEquals(
            MainShellDestination.Tab(MainTab.BROADCAST),
            state.currentDestination,
        )
        assertEquals(FocusContentInsetMode.ScaffoldPadding, state.currentDestination.contentInsetMode)
        assertTrue(state.isBottomBarVisible)
    }

    @Test
    fun `방송 카메라 화면은 edge to edge inset 모드를 사용한다`() {
        val destination = MainShellDestination.BroadcastCamera(
            broadcastId = "broadcast-1",
            streamKey = "stream-key-1",
            hlsUrl = "https://cdn.example.com/live.m3u8",
        )

        assertEquals(FocusContentInsetMode.EdgeToEdge, destination.contentInsetMode)
    }
}
