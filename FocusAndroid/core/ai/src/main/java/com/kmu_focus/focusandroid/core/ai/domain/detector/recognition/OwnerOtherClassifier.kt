package com.kmu_focus.focusandroid.core.ai.domain.detector.recognition

import kotlin.math.sqrt

/**
 * Owner/Other 판별.
 * Master 임베딩은 provider에서 매번 조회 (갤러리 추가 반영).
 */
class OwnerOtherClassifier(
    private val provider: OwnerEmbeddingProvider,
    private val similarityThreshold: Float = 0.4f
) {

    fun decideLabel(embeddings: List<FloatArray>): Pair<Boolean, Float> {
        val masterEmbedding = provider.getMasterEmbeddings()
        if (embeddings.isEmpty() || masterEmbedding.isEmpty()) return false to 0f

        val dim = embeddings.first().size
        val avg = FloatArray(dim)
        for (emb in embeddings) {
            for (i in emb.indices) avg[i] += emb[i]
        }
        for (i in avg.indices) avg[i] /= embeddings.size
        val avgNorm = l2Normalize(avg)

        var maxSimilarity = 0f
        for (ownerEmbeddings in masterEmbedding) {
            var ownerMax = 0f
            for (emb in ownerEmbeddings) {
                val sim = cosineSimilarity(l2Normalize(emb), avgNorm)
                if (sim > ownerMax) ownerMax = sim
            }
            if (ownerMax > maxSimilarity) maxSimilarity = ownerMax
        }
        return (maxSimilarity > similarityThreshold) to maxSimilarity
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        var norm = 0.0
        for (x in v) norm += x * x
        norm = sqrt(norm)
        if (norm < 1e-8) return v
        return FloatArray(v.size) { v[it] / norm.toFloat() }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }
}
