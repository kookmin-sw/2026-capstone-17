package com.kmu_focus.focusandroid.feature.auth.domain.usecase

import com.kmu_focus.focusandroid.feature.auth.domain.session.AuthSessionManager
import javax.inject.Inject

class UpdateAuthSessionUseCase @Inject constructor(
    private val authSessionManager: AuthSessionManager,
) {
    operator fun invoke(isLoggedIn: Boolean) {
        authSessionManager.update(isLoggedIn)
    }
}
