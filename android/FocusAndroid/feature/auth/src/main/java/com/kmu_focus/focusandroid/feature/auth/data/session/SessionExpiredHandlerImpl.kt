package com.kmu_focus.focusandroid.feature.auth.data.session

import com.kmu_focus.focusandroid.core.network.domain.SessionExpiredHandler
import com.kmu_focus.focusandroid.feature.auth.domain.session.AuthSessionManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionExpiredHandlerImpl @Inject constructor(
    private val authSessionManager: AuthSessionManager,
) : SessionExpiredHandler {

    override fun onSessionExpired() {
        authSessionManager.update(false)
    }
}
