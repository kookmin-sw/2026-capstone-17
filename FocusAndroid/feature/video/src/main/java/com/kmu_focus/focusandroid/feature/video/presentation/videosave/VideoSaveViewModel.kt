package com.kmu_focus.focusandroid.feature.video.presentation.videosave

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmu_focus.focusandroid.feature.video.domain.usecase.SaveVideoUseCase
import com.kmu_focus.focusandroid.feature.video.domain.usecase.TranscodeProgress
import com.kmu_focus.focusandroid.feature.video.domain.usecase.TranscodeVideoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VideoSaveUiState(
    val isSaving: Boolean = false,
    val savedFilePath: String? = null,
    val error: String? = null,
    val transcodeProgress: Float = 0f
)

@HiltViewModel
class VideoSaveViewModel @Inject constructor(
    private val saveVideoUseCase: SaveVideoUseCase,
    private val transcodeVideoUseCase: TranscodeVideoUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoSaveUiState())
    val uiState: StateFlow<VideoSaveUiState> = _uiState.asStateFlow()

    fun saveVideo(sourceUri: String) {
        saveVideoInternal(sourceUri) { saveVideoUseCase(sourceUri) }
    }

    fun saveVideoToGallery(sourceUri: String) {
        saveVideoInternal(sourceUri) { saveVideoUseCase.invokeToGallery(sourceUri) }
    }

    fun saveRecording(file: java.io.File) {
        _uiState.value = VideoSaveUiState(isSaving = true)
        viewModelScope.launch {
            try {
                // 임시 파일을 갤러리로 복사
                val sourceUri = android.net.Uri.fromFile(file).toString()
                saveVideoUseCase.invokeToGallery(sourceUri)
                    .onSuccess { path ->
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            savedFilePath = path
                        )
                        // 성공 후 임시 파일 삭제
                        file.delete()
                    }
                    .onFailure { e ->
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            error = e.message ?: "저장 실패"
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message ?: "저장 중 오류 발생"
                )
            }
        }
    }

    // Deprecated: 오프라인 트랜스코딩 사용 안 함
    fun transcodeAndSave(sourceUri: String) {
        // ... (Legacy code, maybe keep for fallback or remove)
        _uiState.value = VideoSaveUiState(isSaving = true)
        viewModelScope.launch {
            transcodeVideoUseCase(sourceUri)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = e.message ?: "트랜스코딩 실패"
                    )
                }
                .collect { progress ->
                    when (progress) {
                        is TranscodeProgress.InProgress -> {
                            _uiState.value = _uiState.value.copy(
                                transcodeProgress = progress.progress
                            )
                        }
                        is TranscodeProgress.Complete -> {
                            _uiState.value = _uiState.value.copy(
                                isSaving = false,
                                savedFilePath = progress.outputPath
                            )
                        }
                        is TranscodeProgress.Error -> {
                            _uiState.value = _uiState.value.copy(
                                isSaving = false,
                                error = progress.message
                            )
                        }
                    }
                }
        }
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
