package com.kmu_focus.focusandroid.feature.auth.domain.session

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AuthSessionManager @Inject constructor() {

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    fun update(isLoggedIn: Boolean) {
        _isLoggedIn.value = isLoggedIn
    }
}
