package com.kmu_focus.focusandroid.core.ai.domain.detector.recognition

import android.graphics.Bitmap

interface OwnerAdder {
    /** 갤러리 등에서 선택한 이미지로 소유자 1명 추가. 성공 시 true. */
    fun addOwnerFromBitmap(bitmap: Bitmap): Boolean
    /** 추출된 임베딩으로 소유자 1명 추가. 성공 시 true. */
    fun addOwnerFromEmbedding(embedding: FloatArray): Boolean
    /** 추출된 임베딩으로 소유자 1명 추가하고 owner 슬롯 id를 반환. 실패 시 null. */
    fun addOwnerFromEmbeddingWithOwnerId(embedding: FloatArray): Int? = null
    /** owner 슬롯의 대표 임베딩을 교체. */
    fun replaceOwnerEmbedding(ownerId: Int, embedding: FloatArray): Boolean = false
    /** 등록된 소유자 전부 삭제. */
    fun clearOwners()
}
