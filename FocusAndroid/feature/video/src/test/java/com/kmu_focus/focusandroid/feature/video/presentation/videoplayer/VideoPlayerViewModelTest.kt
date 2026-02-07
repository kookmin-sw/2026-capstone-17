package com.kmu_focus.focusandroid.feature.video.presentation.videoplayer

import com.kmu_focus.focusandroid.feature.detection.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.feature.video.data.processor.FrameProcessor
import com.kmu_focus.focusandroid.feature.video.domain.entity.ProcessedFrame
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

    private fun createTestBuffer(width: Int = 640, height: Int = 480): ByteBuffer {
        return ByteBuffer.allocateDirect(width * height * 4).apply {
            order(ByteOrder.nativeOrder())
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = VideoPlayerViewModel(frameProcessor)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- 기본 상태 테스트 ---

    @Test
    fun `초기 상태에서 videoUri가 빈 문자열이고 isPlaying이 false임`() = runTest {
        assertEquals("", viewModel.uiState.value.videoUri)
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

    // --- loadVideo 테스트 ---

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
    fun `loadVideo 호출 시 진행 중인 검출이 중지됨`() = runTest {
        viewModel.loadVideo("content://video/1")
        viewModel.startDetection()
        advanceUntilIdle()
        viewModel.loadVideo("content://video/2")

        assertFalse(viewModel.uiState.value.isDetecting)
    }

    // --- Playback 테스트 ---

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
    fun `stopPlayback 호출 시 검출도 중지됨`() = runTest {
        viewModel.loadVideo("content://video/1")
        viewModel.startDetection()
        advanceUntilIdle()
        viewModel.stopPlayback()

        assertFalse(viewModel.uiState.value.isDetecting)
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    // --- Detection 상태 테스트 ---

    @Test
    fun `startDetection 호출 시 isDetecting이 true로 변경됨`() = runTest {
        viewModel.loadVideo("content://video/1")
        viewModel.startDetection()

        assertTrue(viewModel.uiState.value.isDetecting)
    }

    @Test
    fun `stopDetection 호출 시 isDetecting이 false가 되고 faces가 비워짐`() = runTest {
        viewModel.loadVideo("content://video/1")
        viewModel.startDetection()
        viewModel.stopDetection()

        assertFalse(viewModel.uiState.value.isDetecting)
        assertTrue(viewModel.uiState.value.detectedFaces.isEmpty())
    }

    // --- processFrameSync 테스트 ---

    @Test
    fun `processFrameSync 호출 시 동기적으로 detectedFaces가 업데이트됨`() {
        val buffer = createTestBuffer()
        val faces = listOf(DetectedFace(10, 20, 100, 100, 0.9f))
        val frame = ProcessedFrame(faces, 640, 480, 1000L)
        every { frameProcessor.process(buffer, 640, 480, any(), any()) } returns frame

        viewModel.loadVideo("content://video/1")
        viewModel.startDetection()
        viewModel.processFrameSync(buffer, 640, 480)

        assertEquals(1, viewModel.uiState.value.detectedFaces.size)
        assertEquals(0.9f, viewModel.uiState.value.detectedFaces[0].confidence, 0.001f)
    }

    @Test
    fun `processFrameSync 호출 시 frameWidth와 frameHeight가 업데이트됨`() {
        val buffer = createTestBuffer(1280, 720)
        val frame = ProcessedFrame(emptyList(), 1280, 720, 1000L)
        every { frameProcessor.process(buffer, 1280, 720, any(), any()) } returns frame

        viewModel.loadVideo("content://video/1")
        viewModel.startDetection()
        viewModel.processFrameSync(buffer, 1280, 720)

        assertEquals(1280, viewModel.uiState.value.frameWidth)
        assertEquals(720, viewModel.uiState.value.frameHeight)
    }

    @Test
    fun `isDetecting이 false이면 processFrameSync가 FrameProcessor를 호출하지 않음`() {
        val buffer = createTestBuffer()

        viewModel.loadVideo("content://video/1")
        viewModel.processFrameSync(buffer, 640, 480)

        verify(exactly = 0) { frameProcessor.process(any<ByteBuffer>(), any(), any(), any(), any()) }
    }

    @Test
    fun `processFrameSync에서 얼굴 미검출 시 detectedFaces가 비워짐`() {
        val buffer = createTestBuffer()
        val faces = listOf(DetectedFace(10, 20, 100, 100, 0.9f))
        val frameWithFaces = ProcessedFrame(faces, 640, 480, 0L)
        val frameEmpty = ProcessedFrame(emptyList(), 640, 480, 100L)
        every { frameProcessor.process(buffer, 640, 480, any(), any()) } returnsMany listOf(frameWithFaces, frameEmpty)

        viewModel.loadVideo("content://video/1")
        viewModel.startDetection()

        viewModel.processFrameSync(buffer, 640, 480)
        assertEquals(1, viewModel.uiState.value.detectedFaces.size)

        viewModel.processFrameSync(buffer, 640, 480)
        assertTrue(viewModel.uiState.value.detectedFaces.isEmpty())
    }

    @Test
    fun `processFrameSync에서 여러 얼굴이 검출되면 모두 반영됨`() {
        val buffer = createTestBuffer()
        val faces = listOf(
            DetectedFace(10, 20, 100, 100, 0.95f),
            DetectedFace(200, 300, 80, 80, 0.85f),
            DetectedFace(400, 100, 60, 60, 0.75f)
        )
        val frame = ProcessedFrame(faces, 640, 480, 1000L)
        every { frameProcessor.process(buffer, 640, 480, any(), any()) } returns frame

        viewModel.loadVideo("content://video/1")
        viewModel.startDetection()
        viewModel.processFrameSync(buffer, 640, 480)

        assertEquals(3, viewModel.uiState.value.detectedFaces.size)
        assertEquals(0.95f, viewModel.uiState.value.detectedFaces[0].confidence, 0.001f)
        assertEquals(0.85f, viewModel.uiState.value.detectedFaces[1].confidence, 0.001f)
        assertEquals(0.75f, viewModel.uiState.value.detectedFaces[2].confidence, 0.001f)
    }

    @Test
    fun `stopDetection 후 processFrameSync가 FrameProcessor를 호출하지 않음`() {
        val buffer = createTestBuffer()

        viewModel.loadVideo("content://video/1")
        viewModel.startDetection()
        viewModel.stopDetection()
        viewModel.processFrameSync(buffer, 640, 480)

        verify(exactly = 0) { frameProcessor.process(any<ByteBuffer>(), any(), any(), any(), any()) }
    }

    @Test
    fun `processFrameSync 연속 호출 시 매번 FrameProcessor가 호출됨`() {
        val buffer = createTestBuffer()
        val frame = ProcessedFrame(emptyList(), 640, 480, 0L)
        every { frameProcessor.process(buffer, 640, 480, any(), any()) } returns frame

        viewModel.loadVideo("content://video/1")
        viewModel.startDetection()
        viewModel.processFrameSync(buffer, 640, 480)
        viewModel.processFrameSync(buffer, 640, 480)
        viewModel.processFrameSync(buffer, 640, 480)

        verify(exactly = 3) { frameProcessor.process(buffer, 640, 480, any(), any()) }
    }
}
