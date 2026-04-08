package com.kmu_focus.focusandroid.feature.camera.domain.usecase

import com.kmu_focus.focusandroid.feature.camera.domain.repository.CameraRecordingRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CameraRecordingUseCaseTest {

    private val repository: CameraRecordingRepository = mockk(relaxed = true)
    private lateinit var useCase: CameraRecordingUseCase

    @Before
    fun setUp() {
        useCase = CameraRecordingUseCase(repository)
    }

    // --- startRecording 테스트 ---

    @Test
    fun `startRecording 성공 시 Result success에 파일이 담김`() {
        val expectedFile = File.createTempFile("test_recording", ".mp4")
        every { repository.startRecording(1920, 1080, any()) } returns expectedFile

        val result = useCase.startRecording(1920, 1080) { _, _, _ -> }

        assertTrue(result.isSuccess)
        assertEquals(expectedFile, result.getOrNull())
        expectedFile.delete()
    }

    @Test
    fun `startRecording 호출 시 repository에 동일 파라미터가 전달됨`() {
        val file = File.createTempFile("test", ".mp4")
        every { repository.startRecording(1280, 720, any()) } returns file

        useCase.startRecording(1280, 720) { _, _, _ -> }

        verify(exactly = 1) { repository.startRecording(1280, 720, any()) }
        file.delete()
    }

    @Test
    fun `startRecording에서 onSurfaceReady 콜백이 repository에 전달됨`() {
        var capturedCallback: ((Any, Int, Int) -> Unit)? = null
        every { repository.startRecording(any(), any(), any()) } answers {
            capturedCallback = thirdArg()
            File.createTempFile("test", ".mp4")
        }

        val onSurface: (Any, Int, Int) -> Unit = { _, _, _ -> }
        useCase.startRecording(1920, 1080, onSurface)

        assertNotNull(capturedCallback)
    }

    @Test
    fun `startRecording 실패 시 Result failure 반환`() {
        every {
            repository.startRecording(any(), any(), any())
        } throws RuntimeException("encoder init failed")

        val result = useCase.startRecording(1920, 1080) { _, _, _ -> }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    // --- stopRecording 테스트 ---

    @Test
    fun `stopRecording 호출 시 repository에 위임됨`() {
        useCase.stopRecording()

        verify(exactly = 1) { repository.stopRecording() }
    }

    @Test
    fun `stopRecording 실패 시 Result failure 반환`() {
        every { repository.stopRecording() } throws RuntimeException("muxer error")

        val result = useCase.stopRecording()

        assertTrue(result.isFailure)
    }

    @Test
    fun `stopRecording 성공 시 Result success 반환`() {
        every { repository.stopRecording() } returns Unit

        val result = useCase.stopRecording()

        assertTrue(result.isSuccess)
    }
}
