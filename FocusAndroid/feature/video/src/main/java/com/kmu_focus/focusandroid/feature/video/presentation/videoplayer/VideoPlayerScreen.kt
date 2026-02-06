package com.kmu_focus.focusandroid.feature.video.presentation.videoplayer

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

@Composable
fun VideoPlayerScreen(
    videoUri: String,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoPlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(videoUri) {
        viewModel.loadVideo(videoUri)
    }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ExoPlayerView(
            uriString = videoUri,
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
                onClick = {
                    viewModel.stopPlayback()
                    onClearSelection()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("선택 해제")
            }
        }
    }
}

@Composable
private fun ExoPlayerView(
    uriString: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // ExoPlayer는 무거운 네이티브 리소스를 사용하므로 remember로 인스턴스 재생성 방지
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    LaunchedEffect(uriString) {
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(uriString)))
        exoPlayer.prepare()
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) exoPlayer.play() else exoPlayer.pause()
    }

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
