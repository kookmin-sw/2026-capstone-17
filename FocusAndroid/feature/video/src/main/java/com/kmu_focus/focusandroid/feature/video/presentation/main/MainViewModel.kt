package com.kmu_focus.focusandroid.feature.video.presentation.main

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.kmu_focus.focusandroid.feature.video.domain.usecase.AddOwnerFromBitmapUseCase
import com.kmu_focus.focusandroid.feature.video.domain.usecase.ClearOwnersUseCase
import javax.inject.Inject

data class MainUiState(
    val selectedVideoUri: String? = null,
    val addOwnerResult: AddOwnerResult? = null,
    /** 등록된 소유자 이미지 URI (썸네일 표시용, 순서 유지) */
    val addedOwnerUris: List<String> = emptyList()
)

sealed class AddOwnerResult {
    data object Success : AddOwnerResult()
    data object NoFace : AddOwnerResult()
    data object Fail : AddOwnerResult()
    data class Multi(val successCount: Int, val failCount: Int) : AddOwnerResult()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val addOwnerFromBitmapUseCase: AddOwnerFromBitmapUseCase,
    private val clearOwnersUseCase: ClearOwnersUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun onVideoSelected(uri: String) {
        _uiState.value = _uiState.value.copy(selectedVideoUri = uri)
    }

    fun onClearSelection() {
        _uiState.value = _uiState.value.copy(selectedVideoUri = null, addOwnerResult = null)
    }

    /** 단일 추가 (기존 호환). */
    fun addOwnerFromBitmap(bitmap: android.graphics.Bitmap) {
        val result = if (addOwnerFromBitmapUseCase(bitmap)) AddOwnerResult.Success else AddOwnerResult.NoFace
        _uiState.value = _uiState.value.copy(addOwnerResult = result)
    }

    /** 다중 추가. uriForDisplay는 성공 시 목록에 표시할 URI 문자열. */
    fun addOwnersFromBitmaps(bitmapsWithUris: List<Pair<android.graphics.Bitmap, String?>>) {
        var successCount = 0
        var failCount = 0
        val newUris = _uiState.value.addedOwnerUris.toMutableList()
        for ((bitmap, uri) in bitmapsWithUris) {
            if (addOwnerFromBitmapUseCase(bitmap)) {
                successCount++
                if (uri != null) newUris.add(uri)
            } else failCount++
        }
        _uiState.value = _uiState.value.copy(
            addOwnerResult = when {
                successCount > 0 || failCount > 0 -> AddOwnerResult.Multi(successCount, failCount)
                else -> null
            },
            addedOwnerUris = newUris
        )
    }

    fun clearOwners() {
        clearOwnersUseCase()
        _uiState.value = _uiState.value.copy(addedOwnerUris = emptyList())
    }

    fun setAddOwnerResult(result: AddOwnerResult) {
        _uiState.value = _uiState.value.copy(addOwnerResult = result)
    }

    fun clearAddOwnerResult() {
        _uiState.value = _uiState.value.copy(addOwnerResult = null)
    }
}
