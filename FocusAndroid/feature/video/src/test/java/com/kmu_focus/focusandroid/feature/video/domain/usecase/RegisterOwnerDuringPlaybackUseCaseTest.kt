package com.kmu_focus.focusandroid.feature.video.domain.usecase

import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.OwnerAdder
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import com.kmu_focus.focusandroid.feature.video.domain.repository.PlaybackAnalysisRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RegisterOwnerDuringPlaybackUseCaseTest {

    private val repository: PlaybackAnalysisRepository = mockk(relaxed = true)
    private val ownerAdder: OwnerAdder = mockk(relaxed = true)
    private lateinit var useCase: RegisterOwnerDuringPlaybackUseCase

    @Before
    fun setup() {
        useCase = RegisterOwnerDuringPlaybackUseCase(
            playbackAnalysisRepository = repository,
            ownerAdder = ownerAdder,
        )
    }

    @Test
    fun `임베딩 추출 성공 시 OwnerAdder에 등록하고 true 반환`() = runTest {
        val fakeEmbedding = FloatArray(512) { 0.5f }
        val glResult = ProcessedFrame(
            faces = emptyList(),
            frameWidth = 480,
            frameHeight = 270,
            timestampMs = 1000L,
            trackingIds = listOf(3),
        )
        coEvery {
            repository.extractEmbeddingForTrack("content://video/1", 5000L, glResult, 3)
        } returns fakeEmbedding
        every { ownerAdder.addOwnerFromEmbeddingWithOwnerId(fakeEmbedding) } returns 0

        val result = useCase(
            uri = "content://video/1",
            positionMs = 5000L,
            glResult = glResult,
            trackId = 3,
        )

        assertTrue(result)
        verify(exactly = 1) { ownerAdder.addOwnerFromEmbeddingWithOwnerId(fakeEmbedding) }
    }

    @Test
    fun `임베딩 추출 실패 시 false 반환하고 OwnerAdder 호출하지 않음`() = runTest {
        val glResult = ProcessedFrame(
            faces = emptyList(),
            frameWidth = 480,
            frameHeight = 270,
            timestampMs = 1000L,
            trackingIds = listOf(5),
        )
        coEvery {
            repository.extractEmbeddingForTrack(any(), any(), any(), 5)
        } returns null

        val result = useCase(
            uri = "content://video/1",
            positionMs = 3000L,
            glResult = glResult,
            trackId = 5,
        )

        assertFalse(result)
        verify(exactly = 0) { ownerAdder.addOwnerFromEmbeddingWithOwnerId(any()) }
    }

    @Test
    fun `OwnerAdder 등록 실패 시 false 반환`() = runTest {
        val fakeEmbedding = FloatArray(512) { 0.3f }
        val glResult = ProcessedFrame(
            faces = emptyList(),
            frameWidth = 480,
            frameHeight = 270,
            timestampMs = 1000L,
            trackingIds = listOf(7),
        )
        coEvery {
            repository.extractEmbeddingForTrack(any(), any(), any(), 7)
        } returns fakeEmbedding
        every { ownerAdder.addOwnerFromEmbeddingWithOwnerId(fakeEmbedding) } returns null

        val result = useCase(
            uri = "content://video/1",
            positionMs = 2000L,
            glResult = glResult,
            trackId = 7,
        )

        assertFalse(result)
    }

    @Test
    fun `replaceOwnerEmbedding 호출 시 owner 슬롯 임베딩 교체를 시도한다`() = runTest {
        val fakeEmbedding = FloatArray(512) { 0.7f }
        val glResult = ProcessedFrame(
            faces = emptyList(),
            frameWidth = 480,
            frameHeight = 270,
            timestampMs = 1000L,
            trackingIds = listOf(9),
        )
        coEvery { repository.extractEmbeddingForTrack(any(), any(), any(), 9) } returns fakeEmbedding
        every { ownerAdder.replaceOwnerEmbedding(2, fakeEmbedding) } returns true

        val result = useCase.replaceOwnerEmbedding(
            uri = "content://video/1",
            positionMs = 9000L,
            glResult = glResult,
            trackId = 9,
            ownerId = 2,
        )

        assertTrue(result)
        verify(exactly = 1) { ownerAdder.replaceOwnerEmbedding(2, fakeEmbedding) }
    }

    @Test
    fun `saveFaceSnapshotTemporarily는 repository 저장 결과 uri를 반환한다`() = runTest {
        val glResult = ProcessedFrame(
            faces = emptyList(),
            frameWidth = 480,
            frameHeight = 270,
            timestampMs = 1000L,
            trackingIds = listOf(11),
        )
        coEvery {
            repository.saveFaceSnapshotForTrack("content://video/1", 7777L, glResult, 11)
        } returns "content://media/external/images/media/999"

        val result = useCase.saveFaceSnapshotTemporarily(
            uri = "content://video/1",
            positionMs = 7777L,
            glResult = glResult,
            trackId = 11,
        )

        assertEquals("content://media/external/images/media/999", result)
    }
}
