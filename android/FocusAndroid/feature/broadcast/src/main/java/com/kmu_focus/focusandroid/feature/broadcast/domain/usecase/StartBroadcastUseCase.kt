package com.kmu_focus.focusandroid.feature.broadcast.domain.usecase

import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast
import com.kmu_focus.focusandroid.feature.broadcast.domain.repository.BroadcastRepository
import javax.inject.Inject

class StartBroadcastUseCase @Inject constructor(
    private val broadcastRepository: BroadcastRepository,
) {
    suspend operator fun invoke(
        broadcastId: String,
        avatarId: String,
    ): Result<Broadcast> {
        return broadcastRepository.startBroadcast(
            broadcastId = broadcastId,
            avatarId = avatarId,
        )
    }
}
