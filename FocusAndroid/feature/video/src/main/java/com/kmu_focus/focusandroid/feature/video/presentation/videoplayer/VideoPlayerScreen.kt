package com.kmu_focus.focusandroid.feature.video.presentation.videoplayer

import android.view.Surface
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmu_focus.focusandroid.feature.video.data.gl.VideoGLSurfaceView

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
            ExoPlayerGLView(
                uriString = videoUri,
                isPlaying = uiState.isPlaying,
                onFrameCaptured = { buffer, width, height ->
                    viewModel.processFrameSync(buffer, width, height)
                },
                modifier = Modifier.matchParentSize()
            )

            if (uiState.isDetecting && uiState.detectedFaces.isNotEmpty()) {
                FaceDetectionOverlay(
                    faces = uiState.detectedFaces,
                    frameWidth = uiState.frameWidth,
                    frameHeight = uiState.frameHeight,
                    faceLabels = uiState.faceLabels,
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
private fun ExoPlayerGLView(
    uriString: String,
    isPlaying: Boolean,
    onFrameCaptured: (java.nio.ByteBuffer, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val exoPlayer = rememberExoPlayer(uriString = uriString, isPlaying = isPlaying)

    AndroidView(
        factory = { ctx ->
            VideoGLSurfaceView(
                context = ctx,
                onSurfaceReady = { surface: Surface ->
                    exoPlayer.setVideoSurface(surface)
                },
                onFrameCaptured = onFrameCaptured
            )
        },
        update = { },
        onRelease = { glView ->
            exoPlayer.setVideoSurface(null)
            glView.release()
        },
        modifier = modifier
    )
}
