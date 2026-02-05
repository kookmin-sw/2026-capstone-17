package com.kmu_focus.focusandroid.presentation.videoupload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

@Composable
fun VideoUploadScreen(
    modifier: Modifier = Modifier,
    viewModel: VideoUploadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectVideo(it.toString()) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = {
                videoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("동영상 선택")
        }

        when {
            uiState.selectedVideoUri != null -> {
                VideoPlayer(
                    uriString = uiState.selectedVideoUri!!,
                    isPlaying = uiState.isPlaying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { viewModel.togglePlayback() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (uiState.isPlaying) "일시정지" else "재생")
                    }

                    OutlinedButton(
                        onClick = { viewModel.clearSelection() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("선택 해제")
                    }
                }
            }
            uiState.isLoading -> {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun VideoPlayer(
    uriString: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // ExoPlayer 인스턴스를 Composable 생명주기에 맞게 관리
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    // URI 변경 시 MediaItem 교체
    LaunchedEffect(uriString) {
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(uriString)))
        exoPlayer.prepare()
    }

    // isPlaying 상태 동기화
    LaunchedEffect(isPlaying) {
        if (isPlaying) exoPlayer.play() else exoPlayer.pause()
    }

    // Composable 제거 시 ExoPlayer 해제
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
            }
        },
        modifier = modifier
    )
}
