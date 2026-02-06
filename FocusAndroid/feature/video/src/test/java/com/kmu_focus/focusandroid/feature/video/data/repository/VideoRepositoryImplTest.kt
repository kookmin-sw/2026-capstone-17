package com.kmu_focus.focusandroid.feature.video.data.repository

import com.kmu_focus.focusandroid.feature.video.data.local.VideoLocalDataSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class VideoRepositoryImplTest {

    private val localDataSource: VideoLocalDataSource = mockk()
    private val repository = VideoRepositoryImpl(localDataSource)

    @Test
    fun `저장 성공 시 Result success에 파일 경로가 담김`() = runTest {
        val sourceUri = "content://media/video/123"
        val savedPath = "/data/data/com.kmu_focus.focusandroid/files/videos/video_abc.mp4"
        coEvery { localDataSource.copyVideoToInternalStorage(sourceUri) } returns savedPath

        val result = repository.saveVideo(sourceUri)

        assertTrue(result.isSuccess)
        assertEquals(savedPath, result.getOrNull())
    }

    @Test
    fun `DataSource에서 예외 발생 시 Result failure로 래핑됨`() = runTest {
        val sourceUri = "content://media/video/123"
        coEvery { localDataSource.copyVideoToInternalStorage(sourceUri) } throws
            IllegalStateException("URI를 열 수 없습니다")

        val result = repository.saveVideo(sourceUri)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}
