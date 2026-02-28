package com.kmu_focus.focusandroid.presentation

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmu_focus.focusandroid.feature.camera.presentation.CameraScreen
import com.kmu_focus.focusandroid.feature.video.presentation.main.MainScreen
import com.kmu_focus.focusandroid.feature.video.presentation.videosave.VideoSaveViewModel

enum class AppMode {
    VIDEO,
    CAMERA,
}

@Composable
fun AppSelectionScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val saveViewModel: VideoSaveViewModel = hiltViewModel()
    val saveUiState by saveViewModel.uiState.collectAsStateWithLifecycle()
    var selectedMode by rememberSaveable { mutableStateOf<AppMode?>(null) }

    LaunchedEffect(saveUiState.savedFilePath, saveUiState.error) {
        val savedPath = saveUiState.savedFilePath
        val error = saveUiState.error
        when {
            !savedPath.isNullOrBlank() -> {
                Toast.makeText(context, "갤러리에 저장되었습니다.", Toast.LENGTH_SHORT).show()
                saveViewModel.reset()
            }

            !error.isNullOrBlank() -> {
                Toast.makeText(context, "저장 실패: $error", Toast.LENGTH_SHORT).show()
                saveViewModel.reset()
            }
        }
    }

    when (selectedMode) {
        AppMode.VIDEO -> {
            MainScreen(
                modifier = modifier.fillMaxSize(),
                onBackToModeSelection = { selectedMode = null },
            )
        }

        AppMode.CAMERA -> {
            CameraScreen(
                onRecordingComplete = { file ->
                    saveViewModel.saveRecording(file, file.absolutePath)
                },
                onBack = { selectedMode = null },
                modifier = modifier,
            )
        }

        null -> {
            SelectionButtons(
                modifier = modifier,
                onVideoSelected = { selectedMode = AppMode.VIDEO },
                onCameraSelected = { selectedMode = AppMode.CAMERA },
            )
        }
    }
}

@Composable
private fun SelectionButtons(
    modifier: Modifier = Modifier,
    onVideoSelected: () -> Unit,
    onCameraSelected: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("분석 모드를 선택하세요.")
        Button(
            onClick = onVideoSelected,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("동영상 분석")
        }
        Button(
            onClick = onCameraSelected,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("카메라 분석")
        }
    }
}
