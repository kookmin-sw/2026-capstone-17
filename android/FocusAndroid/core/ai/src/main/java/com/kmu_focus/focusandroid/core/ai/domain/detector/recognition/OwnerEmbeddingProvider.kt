package com.kmu_focus.focusandroid.core.ai.domain.detector.recognition

interface OwnerEmbeddingProvider {
    fun getMasterEmbeddings(): List<List<FloatArray>>
}
