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
}
