package com.kmu_focus.focusandroid.feature.video.presentation.main

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmu_focus.focusandroid.feature.video.presentation.videoplayer.VideoPlayerScreen
import com.kmu_focus.focusandroid.feature.video.presentation.videosave.VideoSaveScreen
import com.kmu_focus.focusandroid.feature.video.presentation.videosave.VideoSaveViewModel
import com.kmu_focus.focusandroid.feature.video.presentation.videoupload.VideoUploadScreen
import coil.compose.AsyncImage
import com.kmu_focus.focusandroid.feature.video.domain.config.VideoConfig

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val saveViewModel: VideoSaveViewModel = hiltViewModel()
    val saveUiState by saveViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val multiPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(VideoConfig.MAX_OWNER_PICK)
    ) { uris: List<Uri> ->
        viewModel.addOwnersFromUris(uris)
    }

    LaunchedEffect(uiState.addOwnerResult) {
        when (val r = uiState.addOwnerResult) {
            is AddOwnerResult.Success -> {
                Toast.makeText(context, "소유자가 등록되었습니다", Toast.LENGTH_SHORT).show()
                viewModel.clearAddOwnerResult()
            }
            is AddOwnerResult.NoFace, is AddOwnerResult.Fail -> {
                Toast.makeText(context, "얼굴을 찾을 수 없습니다. 다른 사진을 선택해 주세요.", Toast.LENGTH_SHORT).show()
                viewModel.clearAddOwnerResult()
            }
            is AddOwnerResult.Multi -> {
                val msg = when {
                    r.failCount == 0 -> "${r.successCount}명 등록되었습니다"
                    r.successCount == 0 -> "${r.failCount}명 실패 (얼굴 미검출)"
                    else -> "${r.successCount}명 등록, ${r.failCount}명 실패"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                viewModel.clearAddOwnerResult()
            }
            null -> { }
        }
    }

    var isVideoFullScreen by remember { mutableStateOf(false) }
    val activity = context as? Activity
    val initialRequestedOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    LaunchedEffect(activity, isVideoFullScreen) {
        activity?.requestedOrientation = if (isVideoFullScreen) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            initialRequestedOrientation
        }
    }

    DisposableEffect(activity, initialRequestedOrientation) {
        onDispose {
            activity?.requestedOrientation = initialRequestedOrientation
        }
    }

    LaunchedEffect(saveUiState.savedFilePath) {
        if (saveUiState.savedFilePath != null) {
            viewModel.resetAfterSave()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (!isVideoFullScreen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        multiPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Text("소유자 추가 (여러 명 선택)")
                }
                if (uiState.addedOwnerUris.isNotEmpty()) {
                    OutlinedButton(onClick = { viewModel.clearOwners() }) {
                        Text("전체 삭제")
                    }
                }
            }

            if (uiState.addedOwnerUris.isNotEmpty()) {
                Text(
                    text = "등록된 소유자 (${uiState.addedOwnerUris.size}명)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.addedOwnerUris) { uri ->
                        OwnerThumbnail(uri = uri)
                    }
                }
            }

            VideoUploadScreen(
                onVideoSelected = { uri -> viewModel.onVideoSelected(uri) }
            )

            if (
                uiState.selectedVideoUri != null ||
                saveUiState.isSaving ||
                saveUiState.savedFilePath != null ||
                saveUiState.error != null
            ) {
                VideoSaveScreen(
                    videoUri = uiState.selectedVideoUri.orEmpty(),
                    viewModel = saveViewModel,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        uiState.selectedVideoUri?.let { uri ->
            VideoPlayerScreen(
                videoUri = uri,
                onClearSelection = { viewModel.onClearSelection() },
                modifier = if (isVideoFullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
                isFullScreen = isVideoFullScreen,
                onEnterFullScreen = { isVideoFullScreen = true },
                onExitFullScreen = { isVideoFullScreen = false },
                onPlaybackEnded = { recordedFile ->
                    isVideoFullScreen = false
                    if (recordedFile != null && recordedFile.exists()) {
                        saveViewModel.saveRecording(recordedFile, uri)
                    } else {
                        // 녹화 파일이 없으면 원본 기반 트랜스코딩으로 폴백
                        saveViewModel.transcodeAndSave(uri)
                    }
                }
            )
        }
    }
}

@Composable
private fun OwnerThumbnail(
    uri: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.size(56.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        AsyncImage(
            model = Uri.parse(uri),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
