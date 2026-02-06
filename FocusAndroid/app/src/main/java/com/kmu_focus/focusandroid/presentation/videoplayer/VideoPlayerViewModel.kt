package com.kmu_focus.focusandroid.presentation.videoplayer

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class VideoPlayerUiState(
    val videoUri: String = "",
    val isPlaying: Boolean = false
)

@HiltViewModel
class VideoPlayerViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    fun loadVideo(uri: String) {
        _uiState.value = VideoPlayerUiState(videoUri = uri, isPlaying = false)
    }

    fun togglePlayback() {
        _uiState.value = _uiState.value.copy(
            isPlaying = !_uiState.value.isPlaying
        )
    }

    fun stopPlayback() {
        _uiState.value = _uiState.value.copy(isPlaying = false)
    }
}
