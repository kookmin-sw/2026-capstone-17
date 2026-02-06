package com.kmu_focus.focusandroid.feature.video.presentation.videosave

import com.kmu_focus.focusandroid.feature.video.domain.usecase.SaveVideoUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoSaveViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val saveVideoUseCase: SaveVideoUseCase = mockk()
    private lateinit var viewModel: VideoSaveViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = VideoSaveViewModel(saveVideoUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `초기 상태가 올바름`() = runTest {
        assertFalse(viewModel.uiState.value.isSaving)
        assertNull(viewModel.uiState.value.savedFilePath)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `saveVideo 호출 시 저장 성공하면 savedFilePath가 설정됨`() = runTest {
        val savedPath = "/data/data/com.kmu_focus.focusandroid/files/videos/video.mp4"
        coEvery { saveVideoUseCase(any()) } returns Result.success(savedPath)

        viewModel.saveVideo("content://media/video/123")

        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(savedPath, viewModel.uiState.value.savedFilePath)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `saveVideo 호출 시 저장 실패하면 error가 설정됨`() = runTest {
        coEvery { saveVideoUseCase(any()) } returns
            Result.failure(RuntimeException("파일 저장 실패"))

        viewModel.saveVideo("content://media/video/123")

        assertFalse(viewModel.uiState.value.isSaving)
        assertNull(viewModel.uiState.value.savedFilePath)
        assertEquals("파일 저장 실패", viewModel.uiState.value.error)
    }

    @Test
    fun `reset 호출 시 초기 상태로 리셋됨`() = runTest {
        coEvery { saveVideoUseCase(any()) } returns Result.success("/path/video.mp4")
        viewModel.saveVideo("content://media/video/123")
        assertNotNull(viewModel.uiState.value.savedFilePath)

        viewModel.reset()

        assertEquals(VideoSaveUiState(), viewModel.uiState.value)
    }

    @Test
    fun `새 동영상 저장 시 이전 상태가 초기화됨`() = runTest {
        coEvery { saveVideoUseCase("content://media/video/123") } returns
            Result.success("/path/video1.mp4")
        coEvery { saveVideoUseCase("content://media/video/456") } returns
            Result.success("/path/video2.mp4")

        viewModel.saveVideo("content://media/video/123")
        viewModel.saveVideo("content://media/video/456")

        assertEquals("/path/video2.mp4", viewModel.uiState.value.savedFilePath)
    }
}
