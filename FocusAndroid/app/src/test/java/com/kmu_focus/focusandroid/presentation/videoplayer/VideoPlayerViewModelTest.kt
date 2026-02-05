package com.kmu_focus.focusandroid.presentation.videoplayer

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class VideoPlayerViewModelTest {

    @Test
    fun `초기 상태에서 videoUri가 빈 문자열이고 isPlaying이 false임`() = runTest {
        val viewModel = VideoPlayerViewModel()
        assertEquals("", viewModel.uiState.value.videoUri)
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun `loadVideo 호출 시 videoUri가 업데이트됨`() = runTest {
        val viewModel = VideoPlayerViewModel()
        viewModel.loadVideo("content://media/video/123")
        assertEquals("content://media/video/123", viewModel.uiState.value.videoUri)
    }

    @Test
    fun `loadVideo 호출 시 isPlaying이 false로 초기화됨`() = runTest {
        val viewModel = VideoPlayerViewModel()
        viewModel.loadVideo("content://media/video/123")
        viewModel.togglePlayback()
        assertTrue(viewModel.uiState.value.isPlaying)

        viewModel.loadVideo("content://media/video/456")
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun `togglePlayback 호출 시 isPlaying이 토글됨`() = runTest {
        val viewModel = VideoPlayerViewModel()
        viewModel.loadVideo("content://media/video/123")
        assertFalse(viewModel.uiState.value.isPlaying)

        viewModel.togglePlayback()
        assertTrue(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun `togglePlayback 두 번 호출 시 isPlaying이 false로 돌아감`() = runTest {
        val viewModel = VideoPlayerViewModel()
        viewModel.loadVideo("content://media/video/123")

        viewModel.togglePlayback()
        viewModel.togglePlayback()
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun `stopPlayback 호출 시 isPlaying이 false로 설정됨`() = runTest {
        val viewModel = VideoPlayerViewModel()
        viewModel.loadVideo("content://media/video/123")
        viewModel.togglePlayback()
        assertTrue(viewModel.uiState.value.isPlaying)

        viewModel.stopPlayback()
        assertFalse(viewModel.uiState.value.isPlaying)
    }
}
