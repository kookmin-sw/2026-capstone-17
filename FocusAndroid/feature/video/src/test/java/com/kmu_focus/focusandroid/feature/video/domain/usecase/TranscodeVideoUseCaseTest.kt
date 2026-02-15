package com.kmu_focus.focusandroid.feature.video.domain.usecase

import com.kmu_focus.focusandroid.feature.video.domain.repository.VideoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TranscodeVideoUseCaseTest {

    private val videoRepository: VideoRepository = mockk()
    private val useCase = TranscodeVideoUseCase(videoRepository)

    @Test
    fun `트랜스코딩 성공 시 진행률 Flow를 방출하고 최종 경로를 반환`() = runTest {
        val expectedPath = "/storage/Movies/Focus/transcoded.mp4"
        coEvery { videoRepository.transcodeAndSaveToGallery(any()) } returns flow {
            emit(TranscodeProgress.InProgress(0.3f))
            emit(TranscodeProgress.InProgress(0.7f))
            emit(TranscodeProgress.Complete(expectedPath))
        }

        val results = useCase("content://media/video/123").toList()

        assertEquals(3, results.size)
        assertTrue(results[0] is TranscodeProgress.InProgress)
        assertEquals(0.3f, (results[0] as TranscodeProgress.InProgress).progress)
        assertTrue(results[2] is TranscodeProgress.Complete)
        assertEquals(expectedPath, (results[2] as TranscodeProgress.Complete).outputPath)
        coVerify(exactly = 1) { videoRepository.transcodeAndSaveToGallery("content://media/video/123") }
    }

    @Test
    fun `트랜스코딩 실패 시 Error를 방출`() = runTest {
        coEvery { videoRepository.transcodeAndSaveToGallery(any()) } returns flow {
            emit(TranscodeProgress.InProgress(0.1f))
            emit(TranscodeProgress.Error("디코더 초기화 실패"))
        }

        val results = useCase("content://media/video/123").toList()

        assertEquals(2, results.size)
        assertTrue(results[1] is TranscodeProgress.Error)
        assertEquals("디코더 초기화 실패", (results[1] as TranscodeProgress.Error).message)
    }
}
