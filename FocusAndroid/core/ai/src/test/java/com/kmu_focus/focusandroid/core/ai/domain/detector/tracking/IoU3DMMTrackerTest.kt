package com.kmu_focus.focusandroid.core.ai.domain.detector.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IoU3DMMTrackerTest {

    private lateinit var tracker: IoU3DMMTracker

    @Before
    fun setUp() {
        tracker = IoU3DMMTracker()
        tracker.reset()
    }

    @Test
    fun `빈 detection이면 빈 리스트 반환`() {
        val result = tracker.update(emptyList(), null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `첫 프레임 detection에 순서대로 trackId 할당`() {
        val detections = listOf(
            intArrayOf(10, 20, 100, 100),
            intArrayOf(200, 100, 80, 80)
        )
        val result = tracker.update(detections, null)
        assertEquals(2, result.size)
        assertEquals(0, result[0])
        assertEquals(1, result[1])
    }

    @Test
    fun `동일 bbox 연속 프레임이면 같은 trackId 유지`() {
        val det = intArrayOf(10, 20, 100, 100)
        val r1 = tracker.update(listOf(det), null)
        val r2 = tracker.update(listOf(det), null)
        assertEquals(1, r1.size)
        assertEquals(1, r2.size)
        assertEquals(r1[0], r2[0])
    }

    @Test
    fun `reset 후 trackId가 다시 0부터 시작`() {
        tracker.update(listOf(intArrayOf(0, 0, 50, 50)), null)
        tracker.reset()
        val result = tracker.update(listOf(intArrayOf(10, 10, 50, 50)), null)
        assertEquals(1, result.size)
        assertEquals(0, result[0])
    }

    @Test
    fun `idCoeffs 전달 시 update 호출 가능`() {
        val detections = listOf(intArrayOf(0, 0, 100, 100))
        val idCoeffs = listOf(FloatArray(80) { it * 0.01f })
        val result = tracker.update(detections, idCoeffs)
        assertEquals(1, result.size)
        assertEquals(0, result[0])
    }
}
