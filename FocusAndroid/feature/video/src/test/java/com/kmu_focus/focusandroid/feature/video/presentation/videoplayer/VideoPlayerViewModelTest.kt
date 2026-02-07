package com.kmu_focus.focusandroid.feature.video.presentation.videoplayer

import android.graphics.Bitmap
import com.kmu_focus.focusandroid.feature.detection.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.feature.video.data.processor.FrameProcessor
import com.kmu_focus.focusandroid.feature.video.domain.entity.ProcessedFrame
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val frameProcessor: FrameProcessor = mockk(relaxed = true)
    private lateinit var viewModel: VideoPlayerViewModel

    private fun createMockBitmap(width: Int = 1920, height: Int = 1080): Bitmap {
        return mockk<Bitmap>(relaxed = true) {
            every { this@mockk.width } returns width
            every { this@mockk.height } returns height
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = VideoPlayerViewModel(frameProcessor, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `초기 상태에서 videoUri가 빈 문자열이고 isPlaying이 false임`() = runTest {
        assertEquals("", viewModel.uiState.value.videoUri)
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun `loadVideo 호출 시 videoUri가 업데이트됨`() = runTest {
        viewModel.loadVideo("content://media/video/123")
        assertEquals("content://media/video/123", viewModel.uiState.value.videoUri)
    }

    @Test
    fun `loadVideo 호출 시 isPlaying이 false로 초기화됨`() = runTest {
        viewModel.loadVideo("content://media/video/123")
        viewModel.togglePlayback()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isPlaying)

        viewModel.loadVideo("content://media/video/456")
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun `togglePlayback 호출 시 isPlaying이 토글됨`() = runTest {
        viewModel.loadVideo("content://media/video/123")
        assertFalse(viewModel.uiState.value.isPlaying)

        viewModel.togglePlayback()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun `togglePlayback 두 번 호출 시 isPlaying이 false로 돌아감`() = runTest {
        viewModel.loadVideo("content://media/video/123")

        viewModel.togglePlayback()
        advanceUntilIdle()
        viewModel.togglePlayback()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun `stopPlayback 호출 시 isPlaying이 false로 설정됨`() = runTest {
        viewModel.loadVideo("content://media/video/123")
        viewModel.togglePlayback()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isPlaying)

        viewModel.stopPlayback()
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun `초기 상태에서 detectedFaces가 빈 리스트임`() = runTest {
        assertTrue(viewModel.uiState.value.detectedFaces.isEmpty())
    }

    @Test
    fun `초기 상태에서 isDetecting이 false임`() = runTest {
        assertFalse(viewModel.uiState.value.isDetecting)
    }

    @Test
    fun `startDetection 호출 시 isDetecting이 true로 변경됨`() = runTest {
        viewModel.loadVideo("content://video/1")
        viewModel.startDetection()

        assertTrue(viewModel.uiState.value.isDetecting)
    }

    @Test
    fun `processFrame 호출 시 FrameProcessor가 실행되고 detectedFaces가 업데이트됨`() = runTest {
        val bitmap = createMockBitmap()
        val faces = listOf(DetectedFace(10, 20, 100, 100, 0.95f))
        val frame = ProcessedFrame(faces, 1920, 1080, 1000L)
        every { frameProcessor.process(bitmap, any()) } returns frame

        viewModel.loadVideo("content://video/1")
        viewModel.startDetection()
        viewModel.processFrame(bitmap)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.detectedFaces.size)
        assertEquals(0.95f, viewModel.uiState.value.detectedFaces[0].confidence, 0.001f)
        assertEquals(1920, viewModel.uiState.value.frameWidth)
        assertEquals(1080, viewModel.uiState.value.frameHeight)
        verify { frameProcessor.process(bitmap, any()) }
    }

    @Test
    fun `isDetecting이 false이면 processFrame이 FrameProcessor를 호출하지 않음`() = runTest {
        val bitmap = createMockBitmap()

        viewModel.loadVideo("content://video/1")
        viewModel.processFrame(bitmap)
        advanceUntilIdle()

        verify(exactly = 0) { frameProcessor.process(any(), any()) }
    }

    @Test
    fun `stopDetection 호출 시 isDetecting이 false가 되고 faces가 비워짐`() = runTest {
        viewModel.loadVideo("content://video/1")
        viewModel.startDetection()
        viewModel.stopDetection()

        assertFalse(viewModel.uiState.value.isDetecting)
        assertTrue(viewModel.uiState.value.detectedFaces.isEmpty())
    }

    @Test
    fun `stopPlayback 호출 시 검출도 중지됨`() = runTest {
        viewModel.loadVideo("content://video/1")
        viewModel.startDetection()
        advanceUntilIdle()
        viewModel.stopPlayback()

        assertFalse(viewModel.uiState.value.isDetecting)
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun `loadVideo 호출 시 진행 중인 검출이 중지됨`() = runTest {
        viewModel.loadVideo("content://video/1")
        viewModel.startDetection()
        advanceUntilIdle()
        viewModel.loadVideo("content://video/2")

        assertFalse(viewModel.uiState.value.isDetecting)
    }

    @Test
    fun `얼굴 미검출 시 detectedFaces가 비워짐`() = runTest {
        val bitmap1 = createMockBitmap()
        val bitmap2 = createMockBitmap()
        val faces = listOf(DetectedFace(10, 20, 100, 100, 0.9f))
        every { frameProcessor.process(bitmap1, any()) } returns ProcessedFrame(faces, 1920, 1080, 0L)
        every { frameProcessor.process(bitmap2, any()) } returns ProcessedFrame(emptyList(), 1920, 1080, 100L)

        viewModel.loadVideo("content://video/1")
        viewModel.startDetection()
        viewModel.processFrame(bitmap1)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.detectedFaces.size)

        viewModel.processFrame(bitmap2)
        advanceUntilIdle()

        // 얼굴이 사라지면 박스도 제거
        assertTrue(viewModel.uiState.value.detectedFaces.isEmpty())
    }
}
