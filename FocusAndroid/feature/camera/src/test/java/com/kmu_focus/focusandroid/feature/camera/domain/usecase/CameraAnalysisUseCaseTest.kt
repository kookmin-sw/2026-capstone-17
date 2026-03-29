package com.kmu_focus.focusandroid.feature.camera.domain.usecase

import com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.feature.camera.domain.repository.CameraAnalysisRepository
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CameraAnalysisUseCaseTest {

    private val repository: CameraAnalysisRepository = mockk(relaxed = true)
    private lateinit var useCase: CameraAnalysisUseCase

    private fun createTestBuffer(width: Int = 640, height: Int = 480): ByteBuffer {
        return ByteBuffer.allocateDirect(width * height * 4).apply {
            order(ByteOrder.nativeOrder())
        }
    }

    @Before
    fun setUp() {
        useCase = CameraAnalysisUseCase(repository)
    }

    // --- processFrame 위임 테스트 ---

    @Test
    fun `processFrame 호출 시 repository에 위임됨`() {
        val buffer = createTestBuffer()
        val expected = ProcessedFrame(emptyList(), 640, 480, 1000L)
        every { repository.processFrame(buffer, 640, 480, 1000L) } returns expected

        val result = useCase.processFrame(buffer, 640, 480, 1000L)

        assertEquals(expected, result)
        verify(exactly = 1) { repository.processFrame(buffer, 640, 480, 1000L) }
    }

    @Test
    fun `processFrame에서 단일 얼굴 검출 결과가 올바르게 전달됨`() {
        val buffer = createTestBuffer()
        val faces = listOf(DetectedFace(10, 20, 100, 100, 0.95f))
        val expected = ProcessedFrame(
            faces = faces,
            frameWidth = 640,
            frameHeight = 480,
            timestampMs = 2000L,
            trackingIds = listOf(1),
            faceLabels = listOf(false)
        )
        every { repository.processFrame(buffer, 640, 480, 2000L) } returns expected

        val result = useCase.processFrame(buffer, 640, 480, 2000L)

        assertEquals(1, result.faces.size)
        assertEquals(0.95f, result.faces[0].confidence, 0.001f)
        assertEquals(listOf(1), result.trackingIds)
        assertEquals(listOf(false), result.faceLabels)
    }

    @Test
    fun `processFrame에서 다중 얼굴 검출 결과가 올바르게 전달됨`() {
        val buffer = createTestBuffer()
        val faces = listOf(
            DetectedFace(10, 20, 100, 100, 0.95f),
            DetectedFace(200, 300, 80, 80, 0.85f)
        )
        val expected = ProcessedFrame(
            faces = faces,
            frameWidth = 640,
            frameHeight = 480,
            timestampMs = 3000L,
            trackingIds = listOf(1, 2),
            faceLabels = listOf(true, false)
        )
        every { repository.processFrame(buffer, 640, 480, 3000L) } returns expected

        val result = useCase.processFrame(buffer, 640, 480, 3000L)

        assertEquals(2, result.faces.size)
        assertEquals(listOf(1, 2), result.trackingIds)
        assertEquals(listOf(true, false), result.faceLabels)
    }

    @Test
    fun `processFrame에서 빈 검출 결과가 올바르게 전달됨`() {
        val buffer = createTestBuffer()
        val expected = ProcessedFrame(emptyList(), 640, 480, 500L)
        every { repository.processFrame(buffer, 640, 480, 500L) } returns expected

        val result = useCase.processFrame(buffer, 640, 480, 500L)

        assertTrue(result.faces.isEmpty())
        assertTrue(result.trackingIds.isEmpty())
    }

    @Test
    fun `processFrame에서 대기 상태 라벨이 null로 전달됨`() {
        val buffer = createTestBuffer()
        val faces = listOf(
            DetectedFace(10, 20, 100, 100, 0.9f),
            DetectedFace(200, 300, 80, 80, 0.8f)
        )
        val expected = ProcessedFrame(
            faces = faces,
            frameWidth = 640,
            frameHeight = 480,
            timestampMs = 1000L,
            trackingIds = listOf(1, 2),
            faceLabels = listOf(null, null)
        )
        every { repository.processFrame(buffer, 640, 480, 1000L) } returns expected

        val result = useCase.processFrame(buffer, 640, 480, 1000L)

        assertEquals(listOf(null, null), result.faceLabels)
    }

    // --- clearProcessingThreadCache 위임 테스트 ---

    @Test
    fun `clearProcessingThreadCache 호출 시 repository에 위임됨`() {
        useCase.clearProcessingThreadCache()

        verify(exactly = 1) { repository.clearProcessingThreadCache() }
    }
}
