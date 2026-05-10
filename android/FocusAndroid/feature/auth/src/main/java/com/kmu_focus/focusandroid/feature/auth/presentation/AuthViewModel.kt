package com.kmu_focus.focusandroid.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmu_focus.focusandroid.feature.auth.domain.model.AuthError
import com.kmu_focus.focusandroid.feature.auth.domain.usecase.AutoLoginUseCase
import com.kmu_focus.focusandroid.feature.auth.domain.usecase.KakaoLoginUseCase
import com.kmu_focus.focusandroid.feature.auth.domain.usecase.ServerLoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthUiState {
    object Loading : AuthUiState
    object NeedLogin : AuthUiState
    object Success : AuthUiState
    data class Error(val message: String) : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val kakaoLoginUseCase: KakaoLoginUseCase,
    private val autoLoginUseCase: AutoLoginUseCase,
    private val serverLoginUseCase: ServerLoginUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun tryAutoLogin() {
        viewModelScope.launch {
            val result = runCatching { autoLoginUseCase() }
                .getOrElse { Result.failure(it) }

            _uiState.value = result.fold(
                onSuccess = { isValid ->
                    if (isValid) AuthUiState.Success else AuthUiState.NeedLogin
                },
                onFailure = { throwable -> toUiState(throwable, "자동 로그인 실패") },
            )
        }
    }

    fun kakaoLogin(context: Any) {
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            val kakaoResult = runCatching { kakaoLoginUseCase(context) }
                .getOrElse { Result.failure(it) }

            kakaoResult.fold(
                onSuccess = { kakaoAccessToken ->
                    val serverResult = runCatching { serverLoginUseCase(kakaoAccessToken) }
                        .getOrElse { Result.failure(it) }

                    _uiState.value = serverResult.fold(
                        onSuccess = { AuthUiState.Success },
                        onFailure = { throwable -> toUiState(throwable, "로그인 실패") },
                    )
                },
                onFailure = { throwable ->
                    _uiState.value = toUiState(throwable, "로그인 실패")
                },
            )
        }
    }

    private fun toUiState(
        throwable: Throwable,
        fallbackMessage: String,
    ): AuthUiState {
        return when (throwable) {
            is AuthError.TokenExpired,
            is AuthError.TokenMissing,
            -> AuthUiState.NeedLogin

            else -> AuthUiState.Error(throwable.message ?: fallbackMessage)
        }
    }
}
