package com.kmu_focus.focusandroid.presentation.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmu_focus.focusandroid.presentation.videoplayer.VideoPlayerScreen
import com.kmu_focus.focusandroid.presentation.videoupload.VideoUploadScreen

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        VideoUploadScreen(
            onVideoSelected = { uri -> viewModel.onVideoSelected(uri) }
        )

        uiState.selectedVideoUri?.let { uri ->
            VideoPlayerScreen(
                videoUri = uri,
                onClearSelection = { viewModel.onClearSelection() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
