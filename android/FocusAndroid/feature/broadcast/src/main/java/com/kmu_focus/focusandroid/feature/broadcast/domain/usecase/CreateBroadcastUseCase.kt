package com.kmu_focus.focusandroid.feature.broadcast.domain.usecase

import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast
import com.kmu_focus.focusandroid.feature.broadcast.domain.repository.BroadcastRepository
import javax.inject.Inject

class CreateBroadcastUseCase @Inject constructor(
    private val broadcastRepository: BroadcastRepository,
) {
    suspend operator fun invoke(title: String): Result<Broadcast> {
        if (title.isBlank()) {
            return Result.failure(IllegalArgumentException("방송 제목은 비워둘 수 없습니다"))
        }

        return broadcastRepository.createBroadcast(title.trim())
    }
}
