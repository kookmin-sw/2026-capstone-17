package com.kmu_focus.focusandroid.feature.video.presentation.videoplayer

import android.graphics.SurfaceTexture
import android.net.Uri
import android.view.Surface
import android.view.TextureView
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            ExoPlayerView(
                uriString = videoUri,
                isPlaying = uiState.isPlaying,
                onFrameCaptured = { bitmap ->
                    viewModel.processFrame(bitmap)
                },
                modifier = Modifier.matchParentSize()
            )

            if (uiState.isDetecting && uiState.detectedFaces.isNotEmpty()) {
                FaceDetectionOverlay(
                    faces = uiState.detectedFaces,
                    frameWidth = uiState.frameWidth,
                    frameHeight = uiState.frameHeight,
                    modifier = Modifier.matchParentSize()
                )
            }
        }

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
    onFrameCaptured: (android.graphics.Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        // ExoPlayer에 Surface 연결
                        exoPlayer.setVideoSurface(Surface(surfaceTexture))
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {}

                    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                        exoPlayer.setVideoSurface(null)
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                        // ExoPlayer가 새 프레임을 렌더링할 때마다 호출
                        val bitmap = this@apply.bitmap ?: return
                        onFrameCaptured(bitmap)
                    }
                }
            }
        },
        modifier = modifier
    )
}
