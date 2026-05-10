package com.kmu_focus.focusandroid.feature.auth.domain.usecase

import com.kmu_focus.focusandroid.feature.auth.domain.session.AuthSessionManager
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class ObserveAuthSessionUseCase @Inject constructor(
    private val authSessionManager: AuthSessionManager,
) {
    operator fun invoke(): StateFlow<Boolean> {
        return authSessionManager.isLoggedIn
    }
}
