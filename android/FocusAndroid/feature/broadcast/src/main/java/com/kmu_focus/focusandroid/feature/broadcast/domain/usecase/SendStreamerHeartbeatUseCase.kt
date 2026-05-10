package com.kmu_focus.focusandroid.feature.broadcast.domain.usecase

import com.kmu_focus.focusandroid.feature.broadcast.domain.repository.BroadcastRepository
import javax.inject.Inject

class SendStreamerHeartbeatUseCase @Inject constructor(
    private val broadcastRepository: BroadcastRepository,
) {
    suspend operator fun invoke(broadcastId: String): Result<Unit> {
        return broadcastRepository.sendStreamerHeartbeat(broadcastId)
    }
}
