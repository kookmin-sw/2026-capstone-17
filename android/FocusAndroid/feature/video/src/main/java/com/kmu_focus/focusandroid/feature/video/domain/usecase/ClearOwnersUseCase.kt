package com.kmu_focus.focusandroid.feature.video.domain.usecase

import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.OwnerAdder
import javax.inject.Inject

class ClearOwnersUseCase @Inject constructor(
    private val ownerAdder: OwnerAdder
) {
    operator fun invoke() {
        ownerAdder.clearOwners()
    }
}
