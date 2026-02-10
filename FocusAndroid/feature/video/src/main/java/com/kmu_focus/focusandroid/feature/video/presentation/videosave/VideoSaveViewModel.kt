package com.kmu_focus.focusandroid.feature.video.presentation.videosave

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmu_focus.focusandroid.feature.video.domain.usecase.SaveVideoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VideoSaveUiState(
    val isSaving: Boolean = false,
    val savedFilePath: String? = null,
    val error: String? = null
)

@HiltViewModel
class VideoSaveViewModel @Inject constructor(
    private val saveVideoUseCase: SaveVideoUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoSaveUiState())
    val uiState: StateFlow<VideoSaveUiState> = _uiState.asStateFlow()

    fun saveVideo(sourceUri: String) {
        saveVideoInternal(sourceUri) { saveVideoUseCase(sourceUri) }
    }

    fun saveVideoToGallery(sourceUri: String) {
        saveVideoInternal(sourceUri) { saveVideoUseCase.invokeToGallery(sourceUri) }
    }

    private fun saveVideoInternal(
        sourceUri: String,
        save: suspend () -> Result<String>
    ) {
        _uiState.value = VideoSaveUiState(isSaving = true)
        viewModelScope.launch {
            save()
                .onSuccess { path ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        savedFilePath = path
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = e.message ?: "동영상 저장 실패"
                    )
                }
        }
    }

    fun reset() {
        _uiState.value = VideoSaveUiState()
    }
}
