package com.kmu_focus.focusandroid.feature.video.presentation.videoplayer

import android.view.Surface
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.kmu_focus.focusandroid.feature.video.data.gl.VideoGLSurfaceView
import com.kmu_focus.focusandroid.feature.video.domain.entity.ProcessedFrame
import kotlinx.coroutines.delay
import android.util.Log
import androidx.compose.ui.platform.LocalContext

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
    val context = LocalContext.current
    var glViewRef by remember { mutableStateOf<VideoGLSurfaceView?>(null) }
    val exoPlayer = rememberExoPlayer(
        uriString = videoUri,
        isPlaying = uiState.isPlaying,
        onPlaybackEnded = {
            viewModel.stopPlayback()
            onPlaybackEnded(viewModel.currentRecordingFile)
        }
    )

    DisposableEffect(viewModel, glViewRef) {
        viewModel.setEncoderSurfaceDispatcher { surface, width, height ->
            if (glViewRef == null) {
                Log.w(
                    TAG,
                    "dispatcher invoked but glViewRef is null. surfaceNull=${surface == null}, size=${width}x$height",
                )
            } else {
                Log.w(
                    TAG,
                    "dispatcher invoke -> setEncoderSurface. surfaceNull=${surface == null}, size=${width}x$height",
                )
            }
            glViewRef?.setEncoderSurface(surface, width, height)
        }
        onDispose {
            viewModel.setEncoderSurfaceDispatcher(null)
        }
    }

    LaunchedEffect(videoUri) {
        viewModel.loadVideo(videoUri)
    }

    LaunchedEffect(uiState.lastRegisteredOwnerImageUri) {
        if (uiState.lastRegisteredOwnerImageUri == null) return@LaunchedEffect
        Toast.makeText(context, "등록 얼굴 이미지를 임시 저장했습니다.", Toast.LENGTH_SHORT).show()
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
                onGlSurfaceViewChanged = { glViewRef = it },
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
                FaceTouchOverlay(
                    faces = uiState.detectedFaces,
                    trackingIds = uiState.trackingIds,
                    frameWidth = uiState.frameWidth,
                    frameHeight = uiState.frameHeight,
                    onFaceTapped = { trackId, faceIndex ->
                        viewModel.registerOwnerByTrackId(
                            trackId = trackId,
                            fallbackFaceIndex = faceIndex,
                            positionMsHint = exoPlayer.currentPosition.coerceAtLeast(0L),
                        )
                    },
                    modifier = Modifier.matchParentSize(),
                )
            }

            uiState.lastRegisteredOwnerImageUri?.let { savedImageUri ->
                Card(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.55f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "최근 등록 이미지",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                        AsyncImage(
                            model = savedImageUri,
                            contentDescription = "등록된 얼굴 이미지",
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }

            if (isFullScreen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.toggleControlMenu() },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "재생 메뉴",
                            tint = Color.White
                        )
                    }

                    DropdownMenu(
                        expanded = uiState.isControlMenuExpanded,
                        onDismissRequest = { viewModel.closeControlMenu() }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (uiState.isPlaying) "일시정지" else "재생") },
                            onClick = { viewModel.togglePlayback() }
                        )
                        DropdownMenuItem(
                            text = { Text("나가기") },
                            onClick = {
                                viewModel.stopPlayback()
                                onExitFullScreen()
                                onClearSelection()
                            }
                        )
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
    onFrameCaptured: (java.nio.ByteBuffer, Int, Int) -> ProcessedFrame,
    videoWidth: Int = 0,
    videoHeight: Int = 0,
    onGlSurfaceViewChanged: (VideoGLSurfaceView?) -> Unit = {},
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
            ).also { glView ->
                onGlSurfaceViewChanged(glView)
            }
        },
        update = { glView ->
            (glView as? VideoGLSurfaceView)?.setVideoSize(videoWidth, videoHeight)
        },
        onRelease = { glView ->
            onGlSurfaceViewChanged(null)
            exoPlayer.setVideoSurface(null)
            glView.release()
        },
        modifier = modifier
    )
}

private const val TAG = "VideoPlayerScreen"
