package com.kmu_focus.focusandroid.presentation.videoupload

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class VideoUploadUiState(
    val selectedVideoUri: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class VideoUploadViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(VideoUploadUiState())
    val uiState: StateFlow<VideoUploadUiState> = _uiState.asStateFlow()

    fun selectVideo(uri: String) {
        _uiState.value = _uiState.value.copy(
            selectedVideoUri = uri,
            isLoading = false,
            error = null
        )
    }

    fun clearSelection() {
        _uiState.value = VideoUploadUiState()
    }
}
