package com.kmu_focus.focusandroid.feature.video.presentation.videoplayer

import android.view.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import com.kmu_focus.focusandroid.feature.video.data.gl.VideoGLSurfaceView
import kotlinx.coroutines.delay

@Composable
fun VideoPlayerScreen(
    videoUri: String,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoPlayerViewModel = hiltViewModel(),
    isFullScreen: Boolean = false,
    onEnterFullScreen: () -> Unit = {},
    onExitFullScreen: () -> Unit = {},
    onPlaybackEnded: (java.io.File?) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exoPlayer = rememberExoPlayer(
        uriString = videoUri,
        isPlaying = uiState.isPlaying,
        onPlaybackEnded = {
            viewModel.stopPlayback()
            onPlaybackEnded(viewModel.currentRecordingFile)
        }
    )

    LaunchedEffect(videoUri) {
        viewModel.loadVideo(videoUri)
    }

    LaunchedEffect(uiState.isPlaying, exoPlayer, videoUri) {
        if (!uiState.isPlaying) return@LaunchedEffect
        while (true) {
            viewModel.onPlaybackPosition(exoPlayer.currentPosition)
            delay(33)
        }
    }

    val videoBoxModifier = if (isFullScreen) Modifier.fillMaxSize() else Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)

    Column(
        modifier = if (isFullScreen) modifier.fillMaxSize() else modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(modifier = videoBoxModifier) {
            ExoPlayerGLView(
                exoPlayer = exoPlayer,
                onFrameCaptured = { buffer, width, height ->
                    viewModel.processFrameSync(buffer, width, height)
                },
                videoWidth = uiState.videoWidth,
                videoHeight = uiState.videoHeight,
                encoderSurface = uiState.encoderSurface,
                modifier = Modifier.matchParentSize()
            )

            if (uiState.isDetecting && uiState.detectedFaces.isNotEmpty()) {
                FaceDetectionOverlay(
                    faces = uiState.detectedFaces,
                    frameWidth = uiState.frameWidth,
                    frameHeight = uiState.frameHeight,
                    faceLabels = uiState.faceLabels,
                    trackingIds = uiState.trackingIds,
                    modifier = Modifier.matchParentSize()
                )
            }

            if (isFullScreen) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            onExitFullScreen()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("전체화면 나가기")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.stopPlayback()
                            onExitFullScreen()
                            onClearSelection()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("선택 해제")
                    }
                }
            }
        }

        if (!isFullScreen) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        if (!uiState.isPlaying) onEnterFullScreen()
                        viewModel.togglePlayback()
                    },
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
}

@Composable
private fun ExoPlayerGLView(
    exoPlayer: ExoPlayer,
    onFrameCaptured: (java.nio.ByteBuffer, Int, Int) -> Unit,
    videoWidth: Int = 0,
    videoHeight: Int = 0,
    encoderSurface: Surface? = null,
    modifier: Modifier = Modifier
) {
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
        update = { glView ->
            (glView as? VideoGLSurfaceView)?.setVideoSize(videoWidth, videoHeight)
            (glView as? VideoGLSurfaceView)?.setEncoderSurface(encoderSurface)
        },
        onRelease = { glView ->
            exoPlayer.setVideoSurface(null)
            glView.release()
        },
        modifier = modifier
    )
}
