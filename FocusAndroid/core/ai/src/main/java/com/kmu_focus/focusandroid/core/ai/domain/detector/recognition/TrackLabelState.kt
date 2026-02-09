package com.kmu_focus.focusandroid.core.ai.domain.detector.recognition

/**
 * Track별 임베딩 누적 및 Owner/Other 라벨 관리.
 * 새 ID: skipFrames 스킵 후 collectFrames만큼 수집 후 판별.
 * OTHER: 정면 1회 재검사 (front_face_checked).
 */
class TrackLabelState(
    private val classifier: OwnerOtherClassifier,
    private val skipFrames: Int = 5,
    private val collectFrames: Int = 3
) {

    private data class Entry(
        val embeddings: MutableList<FloatArray>,
        var isOwner: Boolean? = null,
        var similarity: Float = 0f,
        var frontFaceChecked: Boolean = false,
        var framesSeen: Int = 0,
        var wasAbsentLastFrame: Boolean = false
    )

    private val state = mutableMapOf<Int, Entry>()

    fun beginFrame(seenTrackIds: Set<Int>) {
        for ((id, entry) in state) if (id !in seenTrackIds) entry.wasAbsentLastFrame = true
    }

    fun recordFrameSeen(trackId: Int) {
        val entry = state.getOrPut(trackId) { Entry(mutableListOf()) }
        if (entry.wasAbsentLastFrame) {
            entry.embeddings.clear()
            entry.isOwner = null
            entry.similarity = 0f
            entry.frontFaceChecked = false
            entry.framesSeen = 0
            entry.wasAbsentLastFrame = false
        }
        entry.framesSeen++
    }

    fun needsEmbeddingThisFrame(trackId: Int, isFrontal: Boolean): Boolean {
        val entry = state[trackId] ?: return true
        return when (entry.isOwner) {
            null -> entry.framesSeen > skipFrames && entry.embeddings.size < collectFrames && isFrontal
            false -> !entry.frontFaceChecked && isFrontal
            true -> false
        }
    }

    fun addEmbedding(trackId: Int, embedding: FloatArray) {
        val entry = state.getOrPut(trackId) { Entry(mutableListOf()) }
        if (entry.isOwner != null) return
        entry.embeddings.add(embedding)
        if (entry.embeddings.size >= collectFrames) {
            val (isOwner, sim) = classifier.decideLabel(entry.embeddings)
            entry.isOwner = isOwner
            entry.similarity = sim
        }
    }

    fun recheckFrontal(trackId: Int, embedding: FloatArray): Boolean {
        val entry = state[trackId] ?: return false
        if (entry.isOwner != false || entry.frontFaceChecked) return false
        entry.frontFaceChecked = true
        val (isOwner, sim) = classifier.decideLabel(listOf(embedding))
        entry.similarity = sim
        if (isOwner) {
            entry.isOwner = true
            return true
        }
        return false
    }

    fun getLabel(trackId: Int): Boolean? = state[trackId]?.isOwner
    fun getSimilarity(trackId: Int): Float = state[trackId]?.similarity ?: 0f
    fun isPending(trackId: Int): Boolean = state[trackId]?.isOwner == null
    fun getEmbeddingCount(trackId: Int): Int = state[trackId]?.embeddings?.size ?: 0
    fun getFramesSeen(trackId: Int): Int = state[trackId]?.framesSeen ?: 0
    fun getCollectFrames(): Int = collectFrames
    fun getFrontFaceChecked(trackId: Int): Boolean = state[trackId]?.frontFaceChecked ?: false
    fun removeTrack(trackId: Int) { state.remove(trackId) }
    fun clear() { state.clear() }
}
