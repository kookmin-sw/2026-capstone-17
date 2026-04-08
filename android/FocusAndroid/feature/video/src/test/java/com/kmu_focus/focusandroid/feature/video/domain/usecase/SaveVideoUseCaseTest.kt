package com.kmu_focus.focusandroid.feature.video.domain.usecase

import com.kmu_focus.focusandroid.feature.video.domain.repository.VideoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SaveVideoUseCaseTest {

    private val videoRepository: VideoRepository = mockk()
    private val saveVideoUseCase = SaveVideoUseCase(videoRepository)

    @Test
    fun `저장 성공 시 파일 경로를 반환함`() = runTest {
        val sourceUri = "content://media/video/123"
        val savedPath = "/data/data/com.kmu_focus.focusandroid/files/videos/video_123.mp4"
        coEvery { videoRepository.saveVideo(sourceUri) } returns Result.success(savedPath)

        val result = saveVideoUseCase(sourceUri)

        assertTrue(result.isSuccess)
        assertEquals(savedPath, result.getOrNull())
        coVerify(exactly = 1) { videoRepository.saveVideo(sourceUri) }
    }

    @Test
    fun `저장 실패 시 에러를 반환함`() = runTest {
        val sourceUri = "content://media/video/123"
        val exception = RuntimeException("파일 저장 실패")
        coEvery { videoRepository.saveVideo(sourceUri) } returns Result.failure(exception)

        val result = saveVideoUseCase(sourceUri)

        assertTrue(result.isFailure)
        assertEquals("파일 저장 실패", result.exceptionOrNull()?.message)
    }

    // ── 오디오 동시 녹화 후 저장 간소화 테스트 ──

    @Test
    fun `녹화 파일 저장 시 post-mux 없이 바로 갤러리에 저장됨`() = runTest {
        // Given: 녹화 파일에 이미 오디오가 포함되어 있으므로
        // saveRecordingToGallery는 단순 파일 이동만 수행해야 한다
        val recordingFilePath = "/tmp/recording.mp4"
        val galleryUri = "content://media/external/video/456"
        coEvery {
            videoRepository.saveRecordingToGallery(recordingFilePath)
        } returns Result.success(galleryUri)

        // When
        val result = saveVideoUseCase.invokeRecordingToGallery(recordingFilePath)

        // Then: sourceUri 없이 단순 갤러리 이동
        assertTrue(result.isSuccess)
        assertEquals(galleryUri, result.getOrNull())
        coVerify(exactly = 1) {
            videoRepository.saveRecordingToGallery(recordingFilePath)
        }
    }

    @Test
    fun `녹화 파일 갤러리 저장 실패 시 에러 반환`() = runTest {
        val recordingFilePath = "/tmp/recording.mp4"
        val exception = RuntimeException("갤러리 저장 실패")
        coEvery {
            videoRepository.saveRecordingToGallery(recordingFilePath)
        } returns Result.failure(exception)

        val result = saveVideoUseCase.invokeRecordingToGallery(recordingFilePath)

        assertTrue(result.isFailure)
        assertEquals("갤러리 저장 실패", result.exceptionOrNull()?.message)
    }
}
