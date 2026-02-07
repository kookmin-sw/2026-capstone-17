package com.kmu_focus.focusandroid.feature.ai.domain.detector.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingUtilsTest {

    @Test
    fun `intBboxToFloat 변환`() {
        val bbox = intArrayOf(10, 20, 100, 80)
        val float = TrackingUtils.intBboxToFloat(bbox)
        assertEquals(10f, float[0], 1e-6f)
        assertEquals(20f, float[1], 1e-6f)
        assertEquals(100f, float[2], 1e-6f)
        assertEquals(80f, float[3], 1e-6f)
    }

    @Test
    fun `iou 완전 일치 시 1`() {
        val a = intArrayOf(0, 0, 100, 100)
        val b = floatArrayOf(0f, 0f, 100f, 100f)
        assertEquals(1f, TrackingUtils.iou(a, b), 1e-6f)
    }

    @Test
    fun `iou 겹침 없음 시 0`() {
        val a = intArrayOf(0, 0, 50, 50)
        val b = floatArrayOf(100f, 100f, 50f, 50f)
        assertEquals(0f, TrackingUtils.iou(a, b), 1e-6f)
    }

    @Test
    fun `iou 부분 겹침`() {
        val a = intArrayOf(0, 0, 100, 100)
        val b = floatArrayOf(50f, 50f, 100f, 100f)
        val inter = 50 * 50
        val union = 10000 + 10000 - inter
        val expected = inter.toFloat() / union
        assertEquals(expected, TrackingUtils.iou(a, b), 1e-5f)
    }

    @Test
    fun `cosineSimilarity 동일 벡터 1`() {
        val v = floatArrayOf(3f, 4f)
        val norm = TrackingUtils.normalizeL2(v)
        assertEquals(1f, TrackingUtils.cosineSimilarity(norm, norm), 1e-6f)
    }

    @Test
    fun `cosineSimilarity 직교 0`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f)
        assertEquals(0f, TrackingUtils.cosineSimilarity(a, b), 1e-6f)
    }

    @Test
    fun `normalizeL2 단위 벡터 길이 1`() {
        val v = floatArrayOf(3f, 4f)
        val n = TrackingUtils.normalizeL2(v)
        var len = 0f
        for (x in n) len += x * x
        assertEquals(1f, kotlin.math.sqrt(len), 1e-6f)
    }

    @Test
    fun `greedyAssign 전부 매칭`() {
        val cost = Array(2) { FloatArray(2) }
        cost[0][0] = 0.1f
        cost[0][1] = 1f
        cost[1][0] = 1f
        cost[1][1] = 0.2f
        val (matches, unmatchedDets, unmatchedTracks) = TrackingUtils.greedyAssign(2, 2, cost, 1f)
        assertEquals(2, matches.size)
        assertTrue(unmatchedDets.isEmpty())
        assertTrue(unmatchedTracks.isEmpty())
    }

    @Test
    fun `greedyAssign 비용 초과 시 미매칭`() {
        val cost = Array(2) { FloatArray(2) { 1e5f } }
        val (matches, unmatchedDets, unmatchedTracks) = TrackingUtils.greedyAssign(2, 2, cost, 1e4f)
        assertTrue(matches.isEmpty())
        assertEquals(2, unmatchedDets.size)
        assertEquals(2, unmatchedTracks.size)
    }
}
