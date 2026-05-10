package com.kmu_focus.focusandroid.feature.broadcast.domain.usecase

import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast
import com.kmu_focus.focusandroid.feature.broadcast.domain.repository.BroadcastRepository
import javax.inject.Inject

class UpdateBroadcastUseCase @Inject constructor(
    private val broadcastRepository: BroadcastRepository,
) {
    suspend operator fun invoke(
        broadcastId: String,
        title: String,
    ): Result<Broadcast> {
        return broadcastRepository.updateBroadcast(
            broadcastId = broadcastId,
            title = title,
        )
    }
}
