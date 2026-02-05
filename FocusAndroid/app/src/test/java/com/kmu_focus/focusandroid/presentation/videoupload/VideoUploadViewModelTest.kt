package com.kmu_focus.focusandroid.presentation.videoupload

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class VideoUploadViewModelTest {

    @Test
    fun `selectVideo 호출 시 selectedVideoUri가 업데이트됨`() = runTest {
        // Given
        val viewModel = VideoUploadViewModel()
        val testUri = "content://media/video/123"

        // When
        viewModel.selectVideo(testUri)

        // Then
        assertEquals(testUri, viewModel.uiState.value.selectedVideoUri)
    }

    @Test
    fun `selectVideo 호출 시 isLoading이 false이고 error가 null로 설정됨`() = runTest {
        // Given
        val viewModel = VideoUploadViewModel()
        val testUri = "content://media/video/123"

        // When
        viewModel.selectVideo(testUri)

        // Then
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `clearSelection 호출 시 uiState가 초기 상태로 리셋됨`() = runTest {
        // Given
        val viewModel = VideoUploadViewModel()
        val testUri = "content://media/video/123"
        viewModel.selectVideo(testUri)
        assertNotNull(viewModel.uiState.value.selectedVideoUri)

        // When
        viewModel.clearSelection()

        // Then
        assertEquals(VideoUploadUiState(), viewModel.uiState.value)
        assertNull(viewModel.uiState.value.selectedVideoUri)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `여러 번 selectVideo 호출 시 최신 URI로 업데이트됨`() = runTest {
        // Given
        val viewModel = VideoUploadViewModel()
        val firstUri = "content://media/video/123"
        val secondUri = "content://media/video/456"

        // When
        viewModel.selectVideo(firstUri)
        assertEquals(firstUri, viewModel.uiState.value.selectedVideoUri)

        viewModel.selectVideo(secondUri)

        // Then
        assertEquals(secondUri, viewModel.uiState.value.selectedVideoUri)
        assertNotEquals(firstUri, viewModel.uiState.value.selectedVideoUri)
    }
}
