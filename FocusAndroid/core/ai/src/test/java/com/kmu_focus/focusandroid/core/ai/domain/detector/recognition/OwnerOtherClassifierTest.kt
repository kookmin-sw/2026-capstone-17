package com.kmu_focus.focusandroid.core.ai.domain.detector.recognition

import org.junit.Assert.*
import org.junit.Test

class OwnerOtherClassifierTest {

    private fun classifier(master: List<List<FloatArray>>, threshold: Float = 0.4f) =
        OwnerOtherClassifier(object : OwnerEmbeddingProvider {
            override fun getMasterEmbeddings(): List<List<FloatArray>> = master
        }, threshold)

    @Test
    fun `master 비어있으면 항상 false 0f`() {
        val c = classifier(emptyList(), 0.4f)
        val (isOwner, sim) = c.decideLabel(listOf(floatArrayOf(1f, 0f, 0f)))
        assertFalse(isOwner)
        assertEquals(0f, sim, 1e-6f)
    }

    @Test
    fun `embeddings 비어있으면 false 0f`() {
        val c = classifier(listOf(listOf(floatArrayOf(1f, 0f, 0f))), 0.4f)
        val (isOwner, sim) = c.decideLabel(emptyList())
        assertFalse(isOwner)
        assertEquals(0f, sim, 1e-6f)
    }

    @Test
    fun `동일 벡터면 유사도 1이고 threshold 넘으면 owner`() {
        val v = floatArrayOf(1f, 0f, 0f)
        val c = classifier(listOf(listOf(v)), 0.5f)
        val (isOwner, sim) = c.decideLabel(listOf(v))
        assertTrue(isOwner)
        assertEquals(1f, sim, 1e-5f)
    }

    @Test
    fun `threshold 아래면 other`() {
        val master = floatArrayOf(1f, 0f, 0f)
        val other = floatArrayOf(0f, 1f, 0f)
        val c = classifier(listOf(listOf(master)), 0.5f)
        val (isOwner, sim) = c.decideLabel(listOf(other))
        assertFalse(isOwner)
        assertEquals(0f, sim, 1e-6f)
    }
}
