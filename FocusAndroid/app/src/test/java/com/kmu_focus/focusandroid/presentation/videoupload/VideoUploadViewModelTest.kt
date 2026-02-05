package com.kmu_focus.focusandroid.presentation.videoupload

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class VideoUploadViewModelTest {

    @Test
    fun `초기 상태가 올바름`() = runTest {
        val viewModel = VideoUploadViewModel()
        assertNull(viewModel.uiState.value.selectedVideoUri)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `selectVideo 호출 시 selectedVideoUri가 업데이트됨`() = runTest {
        val viewModel = VideoUploadViewModel()
        viewModel.selectVideo("content://media/video/123")
        assertEquals("content://media/video/123", viewModel.uiState.value.selectedVideoUri)
    }

    @Test
    fun `selectVideo 호출 시 isLoading이 false이고 error가 null로 설정됨`() = runTest {
        val viewModel = VideoUploadViewModel()
        viewModel.selectVideo("content://media/video/123")
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `clearSelection 호출 시 uiState가 초기 상태로 리셋됨`() = runTest {
        val viewModel = VideoUploadViewModel()
        viewModel.selectVideo("content://media/video/123")
        assertNotNull(viewModel.uiState.value.selectedVideoUri)

        viewModel.clearSelection()
        assertEquals(VideoUploadUiState(), viewModel.uiState.value)
        assertNull(viewModel.uiState.value.selectedVideoUri)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `여러 번 selectVideo 호출 시 최신 URI로 업데이트됨`() = runTest {
        val viewModel = VideoUploadViewModel()
        viewModel.selectVideo("content://media/video/123")
        viewModel.selectVideo("content://media/video/456")
        assertEquals("content://media/video/456", viewModel.uiState.value.selectedVideoUri)
    }
}
