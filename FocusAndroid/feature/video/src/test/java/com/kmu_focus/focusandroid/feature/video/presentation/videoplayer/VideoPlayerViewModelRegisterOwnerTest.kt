package com.kmu_focus.focusandroid.feature.video.presentation.videoplayer

import com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.core.ai.domain.entity.FaceLandmarks5
import com.kmu_focus.focusandroid.core.ai.domain.entity.Point2f
import com.kmu_focus.focusandroid.feature.video.domain.entity.ProcessedFrame
import com.kmu_focus.focusandroid.feature.video.domain.usecase.AddOwnerFromUriUseCase
import com.kmu_focus.focusandroid.feature.video.domain.usecase.PlaybackAnalysisUseCase
import com.kmu_focus.focusandroid.feature.video.domain.usecase.RecordingUseCase
import com.kmu_focus.focusandroid.feature.video.domain.usecase.RegisterOwnerDuringPlaybackUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlayerViewModelRegisterOwnerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val recordingUseCase: RecordingUseCase = mockk(relaxed = true)
    private val playbackAnalysisUseCase: PlaybackAnalysisUseCase = mockk(relaxed = true)
    private val registerOwnerUseCase: RegisterOwnerDuringPlaybackUseCase = mockk(relaxed = true)
    private val addOwnerFromUriUseCase: AddOwnerFromUriUseCase = mockk(relaxed = true)
    private lateinit var viewModel: VideoPlayerViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { playbackAnalysisUseCase.getVideoDimensions(any()) } returns null
        every { recordingUseCase.startRecording(any(), any(), any()) } returns Result.success(
            File.createTempFile("register-owner", ".mp4")
        )
        viewModel = VideoPlayerViewModel(
            recordingUseCase = recordingUseCase,
            playbackAnalysisUseCase = playbackAnalysisUseCase,
            registerOwnerDuringPlaybackUseCase = registerOwnerUseCase,
            addOwnerFromUriUseCase = addOwnerFromUriUseCase,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `registerOwnerByTrackId 성공 경로 호출 시 예외 없이 동작한다`() = runTest {
        val buffer = prepareDetectingState(trackId = 3)
        coEvery { registerOwnerUseCase.registerOwnerAndGetOwnerId(any(), any(), any(), eq(3)) } returns 0
        coEvery { registerOwnerUseCase.replaceOwnerEmbedding(any(), any(), any(), eq(3), eq(0)) } returns true

        viewModel.registerOwnerByTrackId(3)
        viewModel.processFrameSync(buffer, 640, 480)
        advanceUntilIdle()
    }

    @Test
    fun `registerOwnerByTrackId 실패해도 탭 즉시 Owner 라벨은 반영된다`() = runTest {
        val buffer = prepareDetectingState(trackId = 5)
        coEvery { registerOwnerUseCase.registerOwnerAndGetOwnerId(any(), any(), any(), eq(5)) } returns null

        viewModel.registerOwnerByTrackId(5)
        viewModel.processFrameSync(buffer, 640, 480)
        advanceUntilIdle()

        val labels = viewModel.uiState.value.faceLabels
        assertTrue(labels.any { it == true })
    }

    @Test
    fun `registerOwnerByTrackId 실패 시 저장된 스냅샷 이미지로 owner 등록을 시도한다`() = runTest {
        val buffer = prepareDetectingState(trackId = 6)
        coEvery { registerOwnerUseCase.registerOwnerAndGetOwnerId(any(), any(), any(), eq(6)) } returns null
        coEvery {
            registerOwnerUseCase.saveFaceSnapshotTemporarily(any(), any(), any(), eq(6))
        } returns "content://media/external/images/media/6"
        every { addOwnerFromUriUseCase("content://media/external/images/media/6") } returns true

        viewModel.registerOwnerByTrackId(6)
        viewModel.processFrameSync(buffer, 640, 480)
        advanceUntilIdle()

        verify(exactly = 1) { addOwnerFromUriUseCase("content://media/external/images/media/6") }
    }

    @Test
    fun `재생 중이 아니면 registerOwnerByTrackId가 무시된다`() = runTest {
        viewModel.registerOwnerByTrackId(1)

        coVerify(exactly = 0) { registerOwnerUseCase.registerOwnerAndGetOwnerId(any(), any(), any(), any()) }
        coVerify(exactly = 0) { registerOwnerUseCase.replaceOwnerEmbedding(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `검출 중이 아니면 registerOwnerByTrackId가 무시된다`() = runTest {
        viewModel.togglePlayback() // videoUri 비어있어 isDetecting은 false 유지

        viewModel.registerOwnerByTrackId(1)

        assertFalse(viewModel.uiState.value.isDetecting)
        coVerify(exactly = 0) { registerOwnerUseCase.registerOwnerAndGetOwnerId(any(), any(), any(), any()) }
        coVerify(exactly = 0) { registerOwnerUseCase.replaceOwnerEmbedding(any(), any(), any(), any(), any()) }
    }

    private fun prepareDetectingState(trackId: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(640 * 480 * 4).apply {
            order(ByteOrder.nativeOrder())
        }
        val frame = ProcessedFrame(
            faces = listOf(
                DetectedFace(
                    x = 20,
                    y = 30,
                    width = 100,
                    height = 120,
                    confidence = 0.95f,
                    landmarks = FaceLandmarks5(
                        rightEye = Point2f(50f, 70f),
                        leftEye = Point2f(90f, 70f),
                        nose = Point2f(70f, 86f),
                        rightMouth = Point2f(56f, 112f),
                        leftMouth = Point2f(84f, 112f),
                    ),
                )
            ),
            frameWidth = 640,
            frameHeight = 480,
            timestampMs = 1000L,
            trackingIds = listOf(trackId),
            faceLabels = listOf(null),
        )
        every { playbackAnalysisUseCase.processFrame(any(), eq(640), eq(480), any(), any()) } returns frame

        viewModel.loadVideo("content://video/1")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.togglePlayback()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isPlaying)
        assertTrue(viewModel.uiState.value.isDetecting)
        viewModel.onPlaybackPosition(5000L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.processFrameSync(buffer, 640, 480)
        assertTrue(viewModel.uiState.value.trackingIds.contains(trackId))
        return buffer
    }
}
