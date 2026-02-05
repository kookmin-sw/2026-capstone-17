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

    @Test
    fun `selectVideo 호출 시 isPlaying이 false로 초기화됨`() = runTest {
        // Given
        val viewModel = VideoUploadViewModel()

        // When
        viewModel.selectVideo("content://media/video/123")

        // Then
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun `togglePlayback 호출 시 isPlaying이 토글됨`() = runTest {
        // Given
        val viewModel = VideoUploadViewModel()
        viewModel.selectVideo("content://media/video/123")
        assertFalse(viewModel.uiState.value.isPlaying)

        // When
        viewModel.togglePlayback()

        // Then
        assertTrue(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun `togglePlayback 두 번 호출 시 isPlaying이 false로 돌아감`() = runTest {
        // Given
        val viewModel = VideoUploadViewModel()
        viewModel.selectVideo("content://media/video/123")

        // When
        viewModel.togglePlayback()
        viewModel.togglePlayback()

        // Then
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun `재생 중 새 동영상 선택 시 isPlaying이 false로 리셋됨`() = runTest {
        // Given
        val viewModel = VideoUploadViewModel()
        viewModel.selectVideo("content://media/video/123")
        viewModel.togglePlayback()
        assertTrue(viewModel.uiState.value.isPlaying)

        // When
        viewModel.selectVideo("content://media/video/456")

        // Then
        assertFalse(viewModel.uiState.value.isPlaying)
        assertEquals("content://media/video/456", viewModel.uiState.value.selectedVideoUri)
    }

    @Test
    fun `clearSelection 호출 시 isPlaying이 false로 리셋됨`() = runTest {
        // Given
        val viewModel = VideoUploadViewModel()
        viewModel.selectVideo("content://media/video/123")
        viewModel.togglePlayback()
        assertTrue(viewModel.uiState.value.isPlaying)

        // When
        viewModel.clearSelection()

        // Then
        assertFalse(viewModel.uiState.value.isPlaying)
    }
}
