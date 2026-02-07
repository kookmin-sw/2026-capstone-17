package com.kmu_focus.focusandroid.core.ai.domain.detector.recognition

import android.graphics.Bitmap

interface OwnerAdder {
    /** 갤러리 등에서 선택한 이미지로 소유자 1명 추가. 성공 시 true. */
    fun addOwnerFromBitmap(bitmap: Bitmap): Boolean
    /** 등록된 소유자 전부 삭제. */
    fun clearOwners()
}
