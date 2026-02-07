package com.kmu_focus.focusandroid.feature.video.domain.entity

import com.kmu_focus.focusandroid.feature.detection.domain.entity.DetectedFace
import org.junit.Assert.*
import org.junit.Test

class ProcessedFrameTest {

    @Test
    fun `기본 생성 시 모든 필드가 올바르게 설정됨`() {
        val faces = listOf(
            DetectedFace(10, 20, 100, 100, 0.95f)
        )
        val frame = ProcessedFrame(
            faces = faces,
            frameWidth = 1920,
            frameHeight = 1080,
            timestampMs = 5000L
        )
        assertEquals(1, frame.faces.size)
        assertEquals(1920, frame.frameWidth)
        assertEquals(1080, frame.frameHeight)
        assertEquals(5000L, frame.timestampMs)
    }

    @Test
    fun `빈 faces 리스트로 생성 가능함`() {
        val frame = ProcessedFrame(
            faces = emptyList(),
            frameWidth = 640,
            frameHeight = 480,
            timestampMs = 0L
        )
        assertTrue(frame.faces.isEmpty())
    }

    @Test
    fun `data class 동등성 비교가 작동함`() {
        val face = DetectedFace(10, 20, 100, 100, 0.9f)
        val frame1 = ProcessedFrame(listOf(face), 1920, 1080, 1000L)
        val frame2 = ProcessedFrame(listOf(face), 1920, 1080, 1000L)
        assertEquals(frame1, frame2)
        assertEquals(frame1.hashCode(), frame2.hashCode())
    }

    @Test
    fun `copy를 통한 필드 변경이 작동함`() {
        val frame = ProcessedFrame(emptyList(), 1920, 1080, 0L)
        val copied = frame.copy(timestampMs = 3000L)
        assertEquals(3000L, copied.timestampMs)
        assertEquals(1920, copied.frameWidth)
    }
}
