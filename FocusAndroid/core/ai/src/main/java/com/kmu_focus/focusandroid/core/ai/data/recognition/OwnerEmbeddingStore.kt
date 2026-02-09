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
        if (faces.isEmpty()) {
            Log.w(TAG, "addOwnerFromBitmap: 얼굴 미검출")
            return false
        }
        val face: DetectedFace = faces.first()
        val rect = Rect(
            face.x.coerceIn(0, bitmap.width - 1),
            face.y.coerceIn(0, bitmap.height - 1),
            (face.x + face.width).coerceIn(1, bitmap.width),
            (face.y + face.height).coerceIn(1, bitmap.height)
        )
        if (rect.width() <= 0 || rect.height() <= 0) return false
        val crop = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
        val embedding = embeddingExtractor.extractEmbedding(crop)
        crop.recycle()
        if (embedding == null) {
            Log.w(TAG, "addOwnerFromBitmap: 임베딩 추출 실패")
            return false
        }
        embeddings.add(listOf(embedding))
        Log.i(TAG, "Owner 추가: 총 ${embeddings.size}명")
        return true
    }

    override fun getMasterEmbeddings(): List<List<FloatArray>> = embeddings.toList()

    override fun clearOwners() {
        embeddings.clear()
    }

    fun hasOwners(): Boolean = embeddings.isNotEmpty()
}
