package com.kmu_focus.focusandroid.feature.video.presentation.videosave

import com.kmu_focus.focusandroid.feature.video.domain.usecase.SaveVideoUseCase
import com.kmu_focus.focusandroid.feature.video.domain.usecase.TranscodeProgress
import com.kmu_focus.focusandroid.feature.video.domain.usecase.TranscodeVideoUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
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
    private val transcodeVideoUseCase: TranscodeVideoUseCase = mockk()
    private lateinit var viewModel: VideoSaveViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = VideoSaveViewModel(saveVideoUseCase, transcodeVideoUseCase)
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
        assertEquals(0f, viewModel.uiState.value.transcodeProgress)
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

    // --- 트랜스코딩 관련 테스트 ---

    @Test
    fun `transcodeAndSave 성공 시 진행률 업데이트 후 저장 경로 설정`() = runTest {
        val outputPath = "/storage/Movies/Focus/transcoded.mp4"
        every { transcodeVideoUseCase(any()) } returns flow {
            emit(TranscodeProgress.InProgress(0.5f))
            emit(TranscodeProgress.Complete(outputPath))
        }

        viewModel.transcodeAndSave("content://media/video/123")

        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(outputPath, viewModel.uiState.value.savedFilePath)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `transcodeAndSave 실패 시 error 설정`() = runTest {
        every { transcodeVideoUseCase(any()) } returns flow {
            emit(TranscodeProgress.InProgress(0.2f))
            emit(TranscodeProgress.Error("인코더 생성 실패"))
        }

        viewModel.transcodeAndSave("content://media/video/123")

        assertFalse(viewModel.uiState.value.isSaving)
        assertNull(viewModel.uiState.value.savedFilePath)
        assertEquals("인코더 생성 실패", viewModel.uiState.value.error)
    }

    @Test
    fun `transcodeAndSave 중 예외 발생 시 error 설정`() = runTest {
        every { transcodeVideoUseCase(any()) } returns flow {
            throw RuntimeException("예상치 못한 오류")
        }

        viewModel.transcodeAndSave("content://media/video/123")

        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals("예상치 못한 오류", viewModel.uiState.value.error)
    }
}
