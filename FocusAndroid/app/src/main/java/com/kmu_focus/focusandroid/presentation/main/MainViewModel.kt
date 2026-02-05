package com.kmu_focus.focusandroid.presentation.main

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class MainUiState(
    val selectedVideoUri: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun onVideoSelected(uri: String) {
        _uiState.value = _uiState.value.copy(selectedVideoUri = uri)
    }

    fun onClearSelection() {
        _uiState.value = MainUiState()
    }
}
