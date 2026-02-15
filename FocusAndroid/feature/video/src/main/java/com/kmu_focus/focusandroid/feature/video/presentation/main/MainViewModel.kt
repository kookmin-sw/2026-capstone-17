package com.kmu_focus.focusandroid.feature.video.presentation.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kmu_focus.focusandroid.feature.video.domain.usecase.AddOwnerFromUriUseCase
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
    private val addOwnerFromUriUseCase: AddOwnerFromUriUseCase,
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

    fun addOwnersFromUris(uris: List<Uri>) {
        if (uris.isEmpty()) {
            setAddOwnerResult(AddOwnerResult.Fail)
            return
        }
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    val success = addOwnerFromUriUseCase(uri.toString())
                    success to uri.toString()
                }
            }
            var successCount = 0
            var failCount = 0
            val newUris = _uiState.value.addedOwnerUris.toMutableList()
            for ((success, uriStr) in results) {
                if (success) {
                    successCount++
                    newUris.add(uriStr)
                } else {
                    failCount++
                }
            }
            _uiState.value = _uiState.value.copy(
                addOwnerResult = when {
                    successCount > 0 || failCount > 0 -> AddOwnerResult.Multi(successCount, failCount)
                    else -> null
                },
                addedOwnerUris = newUris
            )
        }
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
