package com.kmu_focus.focusandroid.feature.auth.presentation

import androidx.lifecycle.ViewModel
import com.kmu_focus.focusandroid.feature.auth.domain.usecase.ObserveAuthSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class AuthSessionViewModel @Inject constructor(
    observeAuthSessionUseCase: ObserveAuthSessionUseCase,
) : ViewModel() {
    val isLoggedIn: StateFlow<Boolean> = observeAuthSessionUseCase()
}
