package com.kmu_focus.focusandroid.feature.video.presentation.videosave

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun VideoSaveScreen(
    videoUri: String,
    modifier: Modifier = Modifier,
    viewModel: VideoSaveViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { viewModel.saveVideoToGallery(videoUri) },
            enabled = !uiState.isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (uiState.isSaving) "저장 중..." else "갤러리에 저장")
        }

        OutlinedButton(
            onClick = { viewModel.saveVideo(videoUri) },
            enabled = !uiState.isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("내부 저장소에 저장")
        }

        when {
            uiState.isSaving -> {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
            uiState.error != null -> {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error
                )
            }
            uiState.savedFilePath != null -> {
                Text(
                    text = "저장 완료: ${uiState.savedFilePath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
