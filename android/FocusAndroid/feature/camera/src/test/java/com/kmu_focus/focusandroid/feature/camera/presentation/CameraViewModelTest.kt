package com.kmu_focus.focusandroid.feature.camera.presentation

import com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import com.kmu_focus.focusandroid.feature.camera.domain.entity.LensFacing
import com.kmu_focus.focusandroid.feature.camera.domain.usecase.CameraAnalysisUseCase
import com.kmu_focus.focusandroid.feature.camera.domain.usecase.CameraRecordingUseCase
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val cameraAnalysisUseCase: CameraAnalysisUseCase = mockk(relaxed = true)
    private val cameraRecordingUseCase: CameraRecordingUseCase = mockk(relaxed = true)
    private lateinit var viewModel: CameraViewModel

    private fun createTestBuffer(width: Int = 640, height: Int = 480): ByteBuffer {
        return ByteBuffer.allocateDirect(width * height * 4).apply {
            order(ByteOrder.nativeOrder())
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CameraViewModel(
            cameraAnalysisUseCase,
            cameraRecordingUseCase,
            testDispatcher,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========================================================
    // 초기 상태 테스트
    // ========================================================

    @Test
    fun `초기 상태에서 카메라가 비활성임`() = runTest {
        assertFalse(viewModel.uiState.value.isCameraActive)
    }

    @Test
    fun `초기 상태에서 검출이 비활성임`() = runTest {
        assertFalse(viewModel.uiState.value.isDetecting)
    }

    @Test
    fun `초기 상태에서 녹화가 비활성임`() = runTest {
        assertFalse(viewModel.uiState.value.isRecording)
    }

    @Test
    fun `초기 상태에서 detectedFaces가 빈 리스트임`() = runTest {
        assertTrue(viewModel.uiState.value.detectedFaces.isEmpty())
    }

    @Test
    fun `초기 상태에서 후면 카메라가 선택됨`() = runTest {
        assertEquals(LensFacing.BACK, viewModel.uiState.value.lensFacing)
    }

    @Test
    fun `초기 상태에서 프레임 크기가 0임`() = runTest {
        assertEquals(0, viewModel.uiState.value.frameWidth)
        assertEquals(0, viewModel.uiState.value.frameHeight)
    }

    @Test
    fun `초기 상태에서 trackingIds가 빈 리스트임`() = runTest {
        assertTrue(viewModel.uiState.value.trackingIds.isEmpty())
    }

    @Test
    fun `초기 상태에서 faceLabels가 빈 리스트임`() = runTest {
        assertTrue(viewModel.uiState.value.faceLabels.isEmpty())
    }

    @Test
    fun `초기 상태에서 recordingFile이 null임`() = runTest {
        assertNull(viewModel.uiState.value.recordingFile)
    }

    // ========================================================
    // 카메라 시작/중지 테스트
    // ========================================================

    @Test
    fun `startCamera 호출 시 isCameraActive가 true로 변경됨`() = runTest {
        viewModel.startCamera()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isCameraActive)
    }

    @Test
    fun `stopCamera 호출 시 isCameraActive가 false로 변경됨`() = runTest {
        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.stopCamera()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isCameraActive)
    }

    @Test
    fun `stopCamera 호출 시 검출도 중지됨`() = runTest {
        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.startDetection()
        advanceUntilIdle()
        viewModel.stopCamera()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isDetecting)
    }

    @Test
    fun `stopCamera 호출 시 detectedFaces가 비워짐`() = runTest {
        val buffer = createTestBuffer()
        val faces = listOf(DetectedFace(10, 20, 100, 100, 0.9f))
        val frame = ProcessedFrame(faces, 640, 480, 1000L)
        every { cameraAnalysisUseCase.processFrame(buffer, 640, 480, any()) } returns frame

        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.startDetection()
        viewModel.processFrameSync(buffer, 640, 480)

        viewModel.stopCamera()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.detectedFaces.isEmpty())
    }

    @Test
    fun `stopCamera 호출 시 trackingIds가 비워짐`() = runTest {
        val buffer = createTestBuffer()
        val frame = ProcessedFrame(
            faces = listOf(DetectedFace(10, 20, 100, 100, 0.9f)),
            frameWidth = 640, frameHeight = 480, timestampMs = 1000L,
            trackingIds = listOf(1)
        )
        every { cameraAnalysisUseCase.processFrame(buffer, 640, 480, any()) } returns frame

        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.startDetection()
        viewModel.processFrameSync(buffer, 640, 480)

        viewModel.stopCamera()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.trackingIds.isEmpty())
    }

    @Test
    fun `stopCamera 호출 시 clearProcessingThreadCache가 호출됨`() = runTest {
        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.stopCamera()
        advanceUntilIdle()
        verify(exactly = 1) { cameraAnalysisUseCase.clearProcessingThreadCache() }
    }

    // ========================================================
    // 검출 시작/중지 테스트
    // ========================================================

    @Test
    fun `startDetection 호출 시 isDetecting이 true로 변경됨`() = runTest {
        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.startDetection()
        assertTrue(viewModel.uiState.value.isDetecting)
    }

    @Test
    fun `stopDetection 호출 시 isDetecting이 false가 되고 faces가 비워짐`() = runTest {
        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.startDetection()
        viewModel.stopDetection()
        assertFalse(viewModel.uiState.value.isDetecting)
        assertTrue(viewModel.uiState.value.detectedFaces.isEmpty())
    }

    @Test
    fun `카메라 비활성 상태에서 startDetection 호출해도 isDetecting이 false임`() = runTest {
        viewModel.startDetection()
        assertFalse(viewModel.uiState.value.isDetecting)
    }

    @Test
    fun `stopDetection 호출 시 trackingIds와 faceLabels도 비워짐`() = runTest {
        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.startDetection()
        viewModel.stopDetection()
        assertTrue(viewModel.uiState.value.trackingIds.isEmpty())
        assertTrue(viewModel.uiState.value.faceLabels.isEmpty())
    }

    // ========================================================
    // processFrameSync 테스트
    // ========================================================

    @Test
    fun `processFrameSync 호출 시 detectedFaces가 업데이트됨`() = runTest {
        val buffer = createTestBuffer()
        val faces = listOf(DetectedFace(10, 20, 100, 100, 0.9f))
        val frame = ProcessedFrame(faces, 640, 480, 1000L)
        every { cameraAnalysisUseCase.processFrame(buffer, 640, 480, any()) } returns frame

        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.startDetection()
        viewModel.processFrameSync(buffer, 640, 480)

        assertEquals(1, viewModel.uiState.value.detectedFaces.size)
        assertEquals(0.9f, viewModel.uiState.value.detectedFaces[0].confidence, 0.001f)
    }

    @Test
    fun `processFrameSync 호출 시 frameWidth와 frameHeight가 업데이트됨`() = runTest {
        val buffer = createTestBuffer(1920, 1080)
        val frame = ProcessedFrame(emptyList(), 1920, 1080, 1000L)
        every { cameraAnalysisUseCase.processFrame(buffer, 1920, 1080, any()) } returns frame

        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.startDetection()
        viewModel.processFrameSync(buffer, 1920, 1080)

        assertEquals(1920, viewModel.uiState.value.frameWidth)
        assertEquals(1080, viewModel.uiState.value.frameHeight)
    }

    @Test
    fun `processFrameSync 호출 시 trackingIds와 faceLabels가 업데이트됨`() = runTest {
        val buffer = createTestBuffer()
        val faces = listOf(
            DetectedFace(10, 20, 100, 100, 0.95f),
            DetectedFace(200, 300, 80, 80, 0.85f)
        )
        val frame = ProcessedFrame(
            faces = faces,
            frameWidth = 640, frameHeight = 480, timestampMs = 1000L,
            trackingIds = listOf(1, 2),
            faceLabels = listOf(true, false)
        )
        every { cameraAnalysisUseCase.processFrame(buffer, 640, 480, any()) } returns frame

        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.startDetection()
        viewModel.processFrameSync(buffer, 640, 480)

        assertEquals(listOf(1, 2), viewModel.uiState.value.trackingIds)
        assertEquals(listOf(true, false), viewModel.uiState.value.faceLabels)
    }

    @Test
    fun `isDetecting이 false이면 processFrameSync가 UseCase를 호출하지 않음`() = runTest {
        val buffer = createTestBuffer()

        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.processFrameSync(buffer, 640, 480)

        verify(exactly = 0) { cameraAnalysisUseCase.processFrame(any(), any(), any(), any()) }
    }

    @Test
    fun `processFrameSync에서 여러 얼굴이 검출되면 모두 반영됨`() = runTest {
        val buffer = createTestBuffer()
        val faces = listOf(
            DetectedFace(10, 20, 100, 100, 0.95f),
            DetectedFace(200, 300, 80, 80, 0.85f),
            DetectedFace(400, 100, 60, 60, 0.75f)
        )
        val frame = ProcessedFrame(
            faces = faces,
            frameWidth = 640, frameHeight = 480, timestampMs = 1000L,
            trackingIds = listOf(1, 2, 3),
            faceLabels = listOf(true, false, null)
        )
        every { cameraAnalysisUseCase.processFrame(buffer, 640, 480, any()) } returns frame

        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.startDetection()
        viewModel.processFrameSync(buffer, 640, 480)

        assertEquals(3, viewModel.uiState.value.detectedFaces.size)
        assertEquals(0.95f, viewModel.uiState.value.detectedFaces[0].confidence, 0.001f)
        assertEquals(0.85f, viewModel.uiState.value.detectedFaces[1].confidence, 0.001f)
        assertEquals(0.75f, viewModel.uiState.value.detectedFaces[2].confidence, 0.001f)
    }

    @Test
    fun `processFrameSync에서 얼굴 미검출 시 detectedFaces가 비워짐`() = runTest {
        val buffer = createTestBuffer()
        val frameWithFaces = ProcessedFrame(
            listOf(DetectedFace(10, 20, 100, 100, 0.9f)), 640, 480, 0L
        )
        val frameEmpty = ProcessedFrame(emptyList(), 640, 480, 100L)
        every { cameraAnalysisUseCase.processFrame(buffer, 640, 480, any()) } returnsMany
            listOf(frameWithFaces, frameEmpty)

        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.startDetection()
        viewModel.processFrameSync(buffer, 640, 480)
        assertEquals(1, viewModel.uiState.value.detectedFaces.size)

        viewModel.processFrameSync(buffer, 640, 480)
        assertTrue(viewModel.uiState.value.detectedFaces.isEmpty())
    }

    @Test
    fun `stopDetection 후 processFrameSync가 UseCase를 호출하지 않음`() = runTest {
        val buffer = createTestBuffer()

        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.startDetection()
        viewModel.stopDetection()
        viewModel.processFrameSync(buffer, 640, 480)

        verify(exactly = 0) { cameraAnalysisUseCase.processFrame(any(), any(), any(), any()) }
    }

    @Test
    fun `processFrameSync 연속 호출 시 매번 UseCase가 호출됨`() {
        val buffer = createTestBuffer()
        val frame = ProcessedFrame(emptyList(), 640, 480, 0L)
        every { cameraAnalysisUseCase.processFrame(buffer, 640, 480, any()) } returns frame

        viewModel.startCamera()
        viewModel.startDetection()
        viewModel.processFrameSync(buffer, 640, 480)
        viewModel.processFrameSync(buffer, 640, 480)
        viewModel.processFrameSync(buffer, 640, 480)

        verify(exactly = 3) { cameraAnalysisUseCase.processFrame(buffer, 640, 480, any()) }
    }

    // ========================================================
    // 녹화 시작/중지 테스트
    // ========================================================

    @Test
    fun `startRecording 호출 시 isRecording이 true로 변경됨`() = runTest {
        every {
            cameraRecordingUseCase.startRecording(any(), any(), any())
        } returns Result.success(File.createTempFile("test", ".mp4"))

        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.startDetection()
        viewModel.startRecording(1920, 1080)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isRecording)
    }

    @Test
    fun `startRecording 실패 시 isRecording이 false로 유지됨`() = runTest {
        every {
            cameraRecordingUseCase.startRecording(any(), any(), any())
        } returns Result.failure(RuntimeException("encoder error"))

        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.startDetection()
        viewModel.startRecording(1920, 1080)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRecording)
    }

    @Test
    fun `stopRecording 호출 시 isRecording이 false로 변경됨`() = runTest {
        every {
            cameraRecordingUseCase.startRecording(any(), any(), any())
        } returns Result.success(File.createTempFile("test", ".mp4"))

        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.startDetection()
        viewModel.startRecording(1920, 1080)
        advanceUntilIdle()

        viewModel.stopRecording()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRecording)
    }

    @Test
    fun `stopRecording 호출 시 recordingFile이 설정됨`() = runTest {
        val expectedFile = File.createTempFile("test", ".mp4")
        every {
            cameraRecordingUseCase.startRecording(any(), any(), any())
        } returns Result.success(expectedFile)

        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.startDetection()
        viewModel.startRecording(1920, 1080)
        advanceUntilIdle()

        viewModel.stopRecording()
        advanceUntilIdle()

        assertEquals(expectedFile, viewModel.uiState.value.recordingFile)
        expectedFile.delete()
    }

    @Test
    fun `카메라 비활성 상태에서 startRecording 호출해도 isRecording이 false임`() = runTest {
        viewModel.startRecording(1920, 1080)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRecording)
    }

    @Test
    fun `검출 비활성 상태에서 startRecording 호출해도 isRecording이 false임`() = runTest {
        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.startRecording(1920, 1080)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRecording)
    }

    @Test
    fun `stopCamera 호출 시 녹화도 중지됨`() = runTest {
        every {
            cameraRecordingUseCase.startRecording(any(), any(), any())
        } returns Result.success(File.createTempFile("test", ".mp4"))

        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.startDetection()
        viewModel.startRecording(1920, 1080)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isRecording)

        viewModel.stopCamera()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRecording)
    }

    @Test
    fun `clearRecordingFile 호출 시 recordingFile이 null로 초기화됨`() = runTest {
        val file = File.createTempFile("test", ".mp4")
        every {
            cameraRecordingUseCase.startRecording(any(), any(), any())
        } returns Result.success(file)

        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.startDetection()
        viewModel.startRecording(1920, 1080)
        advanceUntilIdle()
        viewModel.stopRecording()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.recordingFile)

        viewModel.clearRecordingFile()
        assertNull(viewModel.uiState.value.recordingFile)
        file.delete()
    }

    // ========================================================
    // 렌즈 전환 테스트
    // ========================================================

    @Test
    fun `switchLensFacing 호출 시 BACK에서 FRONT로 변경됨`() = runTest {
        assertEquals(LensFacing.BACK, viewModel.uiState.value.lensFacing)

        viewModel.switchLensFacing()
        advanceUntilIdle()

        assertEquals(LensFacing.FRONT, viewModel.uiState.value.lensFacing)
    }

    @Test
    fun `switchLensFacing 두 번 호출 시 BACK으로 돌아감`() = runTest {
        viewModel.switchLensFacing()
        advanceUntilIdle()
        viewModel.switchLensFacing()
        advanceUntilIdle()

        assertEquals(LensFacing.BACK, viewModel.uiState.value.lensFacing)
    }

    @Test
    fun `switchLensFacing 호출 시 검출 결과가 초기화됨`() = runTest {
        val buffer = createTestBuffer()
        val frame = ProcessedFrame(
            listOf(DetectedFace(10, 20, 100, 100, 0.9f)), 640, 480, 0L,
            trackingIds = listOf(1),
            faceLabels = listOf(false)
        )
        every { cameraAnalysisUseCase.processFrame(buffer, 640, 480, any()) } returns frame

        viewModel.startCamera()
        advanceUntilIdle()
        viewModel.startDetection()
        viewModel.processFrameSync(buffer, 640, 480)
        assertEquals(1, viewModel.uiState.value.detectedFaces.size)

        viewModel.switchLensFacing()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.detectedFaces.isEmpty())
        assertTrue(viewModel.uiState.value.trackingIds.isEmpty())
        assertTrue(viewModel.uiState.value.faceLabels.isEmpty())
    }

    @Test
    fun `switchLensFacing 호출 시 clearProcessingThreadCache가 호출됨`() = runTest {
        viewModel.switchLensFacing()
        advanceUntilIdle()

        verify(exactly = 1) { cameraAnalysisUseCase.clearProcessingThreadCache() }
    }
}
