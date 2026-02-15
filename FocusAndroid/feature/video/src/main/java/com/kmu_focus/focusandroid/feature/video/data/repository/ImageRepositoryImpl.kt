package com.kmu_focus.focusandroid.feature.video.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.kmu_focus.focusandroid.feature.video.domain.repository.ImageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ImageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ImageRepository {

    override fun loadBitmap(uri: String): android.graphics.Bitmap? =
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
}
