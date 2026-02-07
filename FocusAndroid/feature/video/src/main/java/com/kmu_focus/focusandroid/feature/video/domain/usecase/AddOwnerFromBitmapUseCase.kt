package com.kmu_focus.focusandroid.feature.video.domain.usecase

import android.graphics.Bitmap
import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.OwnerAdder
import javax.inject.Inject

class AddOwnerFromBitmapUseCase @Inject constructor(
    private val ownerAdder: OwnerAdder
) {
    operator fun invoke(bitmap: Bitmap): Boolean = ownerAdder.addOwnerFromBitmap(bitmap)
}
