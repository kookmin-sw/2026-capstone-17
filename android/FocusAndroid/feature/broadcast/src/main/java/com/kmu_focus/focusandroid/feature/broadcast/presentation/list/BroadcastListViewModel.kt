package com.kmu_focus.focusandroid.feature.broadcast.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.DeleteBroadcastUseCase
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.GetBroadcastListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BroadcastListUiState(
    val broadcasts: List<Broadcast> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class BroadcastListViewModel @Inject constructor(
    private val getBroadcastListUseCase: GetBroadcastListUseCase,
    private val deleteBroadcastUseCase: DeleteBroadcastUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BroadcastListUiState())
    val uiState: StateFlow<BroadcastListUiState> = _uiState.asStateFlow()

    init {
        loadBroadcasts()
    }

    fun refresh() {
        loadBroadcasts()
    }

    fun deleteBroadcast(broadcastId: String) {
        if (broadcastId.isBlank()) {
            _uiState.update { current ->
                current.copy(error = "broadcastId는 비워둘 수 없습니다")
            }
            return
        }

        viewModelScope.launch {
            deleteBroadcastUseCase(broadcastId)
                .onSuccess {
                    _uiState.update { current ->
                        current.copy(
                            broadcasts = current.broadcasts.filterNot { it.broadcastId == broadcastId },
                            error = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(error = throwable.message ?: "방송 삭제 실패")
                    }
                }
        }
    }

    private fun loadBroadcasts(
        page: Int = DEFAULT_PAGE,
        size: Int = DEFAULT_SIZE,
    ) {
        _uiState.update { current ->
            current.copy(isLoading = true, error = null)
        }

        viewModelScope.launch {
            getBroadcastListUseCase(page = page, size = size)
                .onSuccess { broadcasts ->
                    _uiState.update {
                        BroadcastListUiState(
                            broadcasts = broadcasts,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        BroadcastListUiState(
                            broadcasts = emptyList(),
                            isLoading = false,
                            error = throwable.message ?: "방송 목록 조회 실패",
                        )
                    }
                }
        }
    }

    companion object {
        private const val DEFAULT_PAGE = 0
        private const val DEFAULT_SIZE = 20
    }
}
