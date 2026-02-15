package com.kmu_focus.focusandroid.feature.video.domain.repository

import android.graphics.Bitmap

interface ImageRepository {
    fun loadBitmap(uri: String): Bitmap?
}
