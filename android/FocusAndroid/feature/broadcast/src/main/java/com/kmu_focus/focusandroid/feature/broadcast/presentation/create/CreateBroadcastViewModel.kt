package com.kmu_focus.focusandroid.feature.broadcast.presentation.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.CreateBroadcastUseCase
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.StartBroadcastUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DEFAULT_AVATAR_ID = "avatar-a"

data class CreateBroadcastUiState(
    val title: String = "",
    val isCreating: Boolean = false,
    val createdBroadcast: Broadcast? = null,
    val error: String? = null,
)

@HiltViewModel
class CreateBroadcastViewModel @Inject constructor(
    private val createBroadcastUseCase: CreateBroadcastUseCase,
    private val startBroadcastUseCase: StartBroadcastUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateBroadcastUiState())
    val uiState: StateFlow<CreateBroadcastUiState> = _uiState.asStateFlow()

    fun updateTitle(title: String) {
        _uiState.update { current ->
            current.copy(title = title, error = null)
        }
    }

    fun createBroadcast() {
        val title = uiState.value.title.trim()
        if (title.isBlank()) {
            _uiState.update { current ->
                current.copy(error = "방송 제목은 비워둘 수 없습니다")
            }
            return
        }

        _uiState.update { current ->
            current.copy(isCreating = true, error = null)
        }

        viewModelScope.launch {
            createBroadcastUseCase(title)
                .onSuccess { broadcast ->
                    _uiState.update { current ->
                        current.copy(
                            isCreating = false,
                            createdBroadcast = broadcast,
                            error = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            isCreating = false,
                            createdBroadcast = null,
                            error = throwable.message ?: "방송 생성 실패",
                        )
                    }
                }
        }
    }

    fun startBroadcast() {
        val currentState = uiState.value
        val broadcast = currentState.createdBroadcast
        if (broadcast == null) {
            return
        }

        _uiState.update { current ->
            current.copy(isCreating = true, error = null)
        }

        viewModelScope.launch {
            startBroadcastUseCase(
                broadcastId = broadcast.broadcastId,
                avatarId = DEFAULT_AVATAR_ID,
            ).onSuccess { startedBroadcast ->
                _uiState.update { current ->
                    current.copy(
                        isCreating = false,
                        createdBroadcast = startedBroadcast,
                        error = null,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { current ->
                    current.copy(
                        isCreating = false,
                        error = throwable.message ?: "방송 시작 실패",
                    )
                }
            }
        }
    }
}
