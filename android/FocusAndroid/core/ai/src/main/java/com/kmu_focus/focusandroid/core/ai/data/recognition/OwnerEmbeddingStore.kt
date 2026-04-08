package com.kmu_focus.focusandroid.core.ai.data.recognition

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.kmu_focus.focusandroid.core.ai.domain.detector.FaceDetector
import com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.OwnerAdder
import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.OwnerEmbeddingProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 소유자 임베딩 메모리 저장.
 * 갤러리 등에서 추가: addOwnerFromBitmap(bitmap).
 */
@Singleton
class OwnerEmbeddingStore @Inject constructor(
    private val faceDetector: FaceDetector,
    private val embeddingExtractor: ArcFaceEmbeddingExtractor
) : OwnerEmbeddingProvider, OwnerAdder {

    companion object {
        private const val TAG = "OwnerEmbeddingStore"
        private const val MIN_FACE_CONFIDENCE = 0.5f
    }

    private val embeddings = mutableListOf<List<FloatArray>>()

    override fun addOwnerFromBitmap(bitmap: Bitmap): Boolean {
        val faces = faceDetector.detectFaces(bitmap).filter { it.confidence >= MIN_FACE_CONFIDENCE }
        val embedding = if (faces.isNotEmpty()) {
            extractEmbeddingFromDetectedFace(bitmap, faces.first())
                ?: run {
                    // 이미 얼굴 crop인 경우 재검출 실패가 잦아 전체 이미지 임베딩을 fallback으로 사용.
                    Log.w(TAG, "addOwnerFromBitmap: 얼굴 crop 임베딩 실패, 전체 이미지 fallback 시도")
                    embeddingExtractor.extractEmbedding(bitmap)
                }
        } else {
            Log.w(TAG, "addOwnerFromBitmap: 얼굴 미검출, 전체 이미지 fallback 시도")
            embeddingExtractor.extractEmbedding(bitmap)
        }
        if (embedding == null) {
            Log.w(TAG, "addOwnerFromBitmap: 임베딩 추출 실패")
            return false
        }
        embeddings.add(listOf(embedding))
        Log.i(TAG, "Owner 추가: 총 ${embeddings.size}명")
        return true
    }

    override fun addOwnerFromEmbedding(embedding: FloatArray): Boolean {
        return addOwnerFromEmbeddingWithOwnerId(embedding) != null
    }

    override fun addOwnerFromEmbeddingWithOwnerId(embedding: FloatArray): Int? {
        if (embedding.isEmpty()) {
            Log.w(TAG, "addOwnerFromEmbeddingWithOwnerId: 빈 임베딩")
            return null
        }
        embeddings.add(listOf(embedding.copyOf()))
        val ownerId = embeddings.lastIndex
        Log.i(TAG, "Owner(embedding) 추가: ownerId=$ownerId, 총 ${embeddings.size}명")
        return ownerId
    }

    override fun replaceOwnerEmbedding(ownerId: Int, embedding: FloatArray): Boolean {
        if (ownerId !in embeddings.indices) {
            Log.w(TAG, "replaceOwnerEmbedding: invalid ownerId=$ownerId")
            return false
        }
        if (embedding.isEmpty()) {
            Log.w(TAG, "replaceOwnerEmbedding: 빈 임베딩")
            return false
        }
        embeddings[ownerId] = listOf(embedding.copyOf())
        Log.i(TAG, "replaceOwnerEmbedding: ownerId=$ownerId 교체 완료")
        return true
    }

    override fun getMasterEmbeddings(): List<List<FloatArray>> = embeddings.toList()

    override fun clearOwners() {
        embeddings.clear()
    }

    fun hasOwners(): Boolean = embeddings.isNotEmpty()

    private fun extractEmbeddingFromDetectedFace(
        bitmap: Bitmap,
        face: DetectedFace,
    ): FloatArray? {
        val rect = Rect(
            face.x.coerceIn(0, bitmap.width - 1),
            face.y.coerceIn(0, bitmap.height - 1),
            (face.x + face.width).coerceIn(1, bitmap.width),
            (face.y + face.height).coerceIn(1, bitmap.height)
        )
        if (rect.width() <= 0 || rect.height() <= 0) return null
        val crop = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
        return try {
            embeddingExtractor.extractEmbedding(crop)
        } finally {
            crop.recycle()
        }
    }
}
