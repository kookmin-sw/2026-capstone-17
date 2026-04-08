package com.kmu_focus.focusandroid.feature.video.domain.usecase

import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.OwnerAdder
import com.kmu_focus.focusandroid.feature.video.domain.repository.ImageRepository
import javax.inject.Inject

class AddOwnerFromUriUseCase @Inject constructor(
    private val imageRepository: ImageRepository,
    private val ownerAdder: OwnerAdder
) {
    operator fun invoke(uri: String): Boolean {
        val bitmap = imageRepository.loadBitmap(uri) ?: return false
        return ownerAdder.addOwnerFromBitmap(bitmap)
    }
}
