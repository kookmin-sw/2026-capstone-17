package com.kmu_focus.focusandroid.feature.broadcast.presentation.camera

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kmu_focus.focusandroid.core.ui.insets.focusSafeDrawingPadding
import com.kmu_focus.focusandroid.core.grpc.data.repository.GrpcMetadataRepositoryImpl
import com.kmu_focus.focusandroid.core.streaming.domain.entity.SrtConnectionState
import com.kmu_focus.focusandroid.feature.camera.domain.entity.LensFacing
import com.kmu_focus.focusandroid.feature.camera.presentation.CameraScreen
import com.kmu_focus.focusandroid.feature.camera.presentation.CameraViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import kotlinx.coroutines.delay

private const val DEFAULT_AVATAR_ID = "avatar-a"
private const val DEFAULT_BROADCAST_WIDTH = 1280
private const val DEFAULT_BROADCAST_HEIGHT = 720
private const val BROADCAST_START_DELAY_MS = 2_000L
private const val RECORDING_START_TIMEOUT_MS = 5_000L
private const val START_API_TIMEOUT_MS = 12_000L

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BroadcastCameraEntryPoint {
    fun grpcMetadataRepository(): GrpcMetadataRepositoryImpl
}

@Composable
fun BroadcastCameraScreen(
    broadcastId: String,
    streamKey: String,
    hlsUrl: String,
    onBack: () -> Unit,
    viewModel: BroadcastCameraViewModel = hiltViewModel(),
    cameraViewModel: CameraViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val cameraUiState by cameraViewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val metadataRepository = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            BroadcastCameraEntryPoint::class.java,
        ).grpcMetadataRepository()
    }

    var hasStartedRecorder by rememberSaveable(uiState.broadcastId) { mutableStateOf(false) }
    var hasRequestedServerStart by rememberSaveable(uiState.broadcastId) { mutableStateOf(false) }
    var isMenuPresented by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(broadcastId, streamKey, hlsUrl) {
        viewModel.updateSession(
            broadcastId = broadcastId,
            streamKey = streamKey,
            hlsUrl = hlsUrl,
        )
    }

    LaunchedEffect(cameraUiState.isCameraActive) {
        if (cameraUiState.isCameraActive && !cameraUiState.isDetecting) {
            cameraViewModel.startDetection()
        }
    }

    LaunchedEffect(uiState.isPreparing, uiState.isBroadcasting) {
        if (!uiState.isPreparing && !uiState.isBroadcasting) {
            hasStartedRecorder = false
            hasRequestedServerStart = false
        }
    }

    LaunchedEffect(
        uiState.isPreparing,
        hasStartedRecorder,
        cameraUiState.isRecording,
        viewModel.currentMuxerFactory,
        uiState.broadcastId,
    ) {
        val muxerFactory = viewModel.currentMuxerFactory ?: return@LaunchedEffect
        if (!uiState.isPreparing || hasStartedRecorder || cameraUiState.isRecording) {
            return@LaunchedEffect
        }

        hasStartedRecorder = true
        cameraViewModel.startBroadcastRecording(
            width = DEFAULT_BROADCAST_WIDTH,
            height = DEFAULT_BROADCAST_HEIGHT,
            muxerFactory = muxerFactory,
            metadataRepository = metadataRepository,
            sessionId = uiState.broadcastId,
        )
    }

    LaunchedEffect(
        uiState.isPreparing,
        hasStartedRecorder,
        cameraUiState.isRecording,
    ) {
        if (!uiState.isPreparing || !hasStartedRecorder || cameraUiState.isRecording) {
            return@LaunchedEffect
        }

        delay(RECORDING_START_TIMEOUT_MS)
        if (uiState.isPreparing && hasStartedRecorder && !cameraUiState.isRecording) {
            viewModel.cancelPreparingBroadcast(message = "SRT 송출을 시작하지 못했습니다")
        }
    }

    LaunchedEffect(
        uiState.isPreparing,
        cameraUiState.isRecording,
        uiState.broadcastId,
    ) {
        if (!uiState.isPreparing || !cameraUiState.isRecording || hasRequestedServerStart) {
            return@LaunchedEffect
        }

        hasRequestedServerStart = true
        viewModel.markStreamingConnected()
        delay(BROADCAST_START_DELAY_MS)
        viewModel.confirmBroadcastStarted(DEFAULT_AVATAR_ID) {
            cameraViewModel.stopRecording()
            viewModel.cancelPreparingBroadcast(clearError = false)
        }
    }

    LaunchedEffect(
        uiState.isPreparing,
        hasRequestedServerStart,
        uiState.isBroadcasting,
        uiState.broadcastId,
    ) {
        if (!uiState.isPreparing || !hasRequestedServerStart || uiState.isBroadcasting) {
            return@LaunchedEffect
        }

        delay(START_API_TIMEOUT_MS)
        if (uiState.isPreparing && hasRequestedServerStart && !uiState.isBroadcasting) {
            if (cameraUiState.isRecording) {
                cameraViewModel.stopRecording()
            }
            viewModel.cancelPreparingBroadcast(message = "방송 시작이 지연되고 있습니다")
        }
    }

    val displayedHlsUrl = if (uiState.hlsUrl.isNotBlank()) uiState.hlsUrl else hlsUrl
    val handleExit = {
        if (cameraUiState.isRecording) {
            cameraViewModel.stopRecording()
        }
        if (uiState.isPreparing) {
            viewModel.cancelPreparingBroadcast()
        } else {
            viewModel.stopBroadcasting()
        }
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        CameraScreen(
            onRecordingComplete = {},
            modifier = Modifier.fillMaxSize(),
            onBack = handleExit,
            showDetectionControl = false,
            showRecordingControl = false,
            showMenuButton = false,
            showStatusPanel = false,
            lockLandscapeOrientation = true,
            viewModel = cameraViewModel,
        )

        if (isMenuPresented) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable { isMenuPresented = false },
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .focusSafeDrawingPadding(
                    sides = WindowInsetsSides.Top + WindowInsetsSides.Start,
                    start = 22.dp,
                    top = 18.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (uiState.isBroadcasting) {
                OverlayChip(
                    text = "LIVE",
                    containerColor = Color(0xFFD14343).copy(alpha = 0.88f),
                    contentColor = Color.White,
                )
            }
            OverlayChip(
                text = if (cameraUiState.isDetecting) "Camera Ready" else "Camera Loading",
                containerColor = Color(0xFF0369A1).copy(alpha = 0.78f),
                contentColor = Color.White,
            )
            OverlayChip(
                text = "Avatar 기본",
                containerColor = Color.White.copy(alpha = 0.18f),
                contentColor = Color.White,
            )
        }

        MenuButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .focusSafeDrawingPadding(
                    sides = WindowInsetsSides.Top + WindowInsetsSides.End,
                    top = 22.dp,
                    end = 22.dp,
                ),
            onClick = { isMenuPresented = !isMenuPresented },
        )

        FloatingBroadcastAction(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .focusSafeDrawingPadding(
                    sides = WindowInsetsSides.Bottom + WindowInsetsSides.End,
                    end = 24.dp,
                    bottom = 26.dp,
                ),
            label = when {
                uiState.isBroadcasting -> "방송 종료하기"
                uiState.isPreparing -> "준비 취소"
                else -> "방송 준비하기"
            },
            onClick = {
                when {
                    uiState.isBroadcasting -> {
                        cameraViewModel.stopRecording()
                        viewModel.stopBroadcasting()
                    }

                    uiState.isPreparing -> {
                        if (cameraUiState.isRecording) {
                            cameraViewModel.stopRecording()
                        }
                        viewModel.cancelPreparingBroadcast()
                    }

                    else -> viewModel.prepareBroadcasting()
                }
            },
        )

        AnimatedVisibility(
            visible = isMenuPresented,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        ) {
            BroadcastMenuPanel(
                broadcastId = uiState.broadcastId,
                streamKey = uiState.streamKey,
                hlsUrl = displayedHlsUrl,
                error = uiState.error,
                srtState = uiState.srtState,
                lensFacing = cameraUiState.lensFacing,
                ownerThumbnailPaths = cameraUiState.registeredOwnerThumbnails,
                onDismiss = { isMenuPresented = false },
                onExit = handleExit,
                onCopyHls = {
                    copyToClipboard(context, displayedHlsUrl)
                    Toast.makeText(context, "HLS 링크를 복사했습니다.", Toast.LENGTH_SHORT).show()
                },
                onSelectLensFacing = { target ->
                    if (cameraUiState.lensFacing != target) {
                        cameraViewModel.switchLensFacing()
                    }
                },
            )
        }
    }
}

@Composable
private fun MenuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0369A1).copy(alpha = 0.78f),
        shadowElevation = 10.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "≡",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
private fun OverlayChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun FloatingBroadcastAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = Color.Transparent,
        shadowElevation = 12.dp,
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFDB1AFF),
                            Color(0xFF3D1FFF),
                        ),
                    ),
                    shape = RoundedCornerShape(999.dp),
                )
                .padding(horizontal = 38.dp, vertical = 16.dp),
        ) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun BroadcastMenuPanel(
    broadcastId: String,
    streamKey: String,
    hlsUrl: String,
    error: String?,
    srtState: SrtConnectionState,
    lensFacing: LensFacing,
    ownerThumbnailPaths: List<String>,
    onDismiss: () -> Unit,
    onExit: () -> Unit,
    onCopyHls: () -> Unit,
    onSelectLensFacing: (LensFacing) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(340.dp),
        color = Color.White.copy(alpha = 0.94f),
        shadowElevation = 18.dp,
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = 30.dp, start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "Broadcast Control",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.Black.copy(alpha = 0.88f),
            )

            PanelSection(
                title = "Owner 관리",
                subtitle = "화면에 그대로 유지할 스트리머 프로필",
            ) {
                if (ownerThumbnailPaths.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(
                            items = ownerThumbnailPaths,
                            key = { it },
                        ) { path ->
                            OwnerThumbnailCard(
                                path = path,
                                label = "OWNER",
                            )
                        }
                    }
                } else {
                    EmptyPanelHint("얼굴을 탭해 owner를 등록하면 여기에 표시됩니다.")
                }
            }

            PanelSection(
                title = "카메라",
                subtitle = "렌즈 방향 전환",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LensToggleChip(
                        text = "전면",
                        selected = lensFacing == LensFacing.FRONT,
                        onClick = { onSelectLensFacing(LensFacing.FRONT) },
                    )
                    LensToggleChip(
                        text = "후면",
                        selected = lensFacing == LensFacing.BACK,
                        onClick = { onSelectLensFacing(LensFacing.BACK) },
                    )
                }
            }

            PanelSection(
                title = "개인정보 처리",
                subtitle = "현재 안드로이드 구조에서는 Avatar 기본 모드로 방송을 시작합니다.",
            ) {
                OverlayChip(
                    text = "Avatar 기본",
                    containerColor = Color(0xFFEEF2FF),
                    contentColor = Color(0xFF4338CA),
                )
            }

            PanelSection(
                title = "방송 상태",
                subtitle = "세션 및 송출 연결 정보",
            ) {
                MetaItem(label = "Session ID", value = broadcastId.ifBlank { "-" })
                MetaItem(label = "Stream Key", value = streamKey.ifBlank { "-" })
                MetaItem(label = "SRT", value = srtState.name)
                if (hlsUrl.isNotBlank()) {
                    MetaItem(label = "HLS", value = hlsUrl)
                    OutlinedButton(
                        onClick = onCopyHls,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("HLS 링크 복사")
                    }
                }
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("닫기")
                }
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("나가기")
                }
            }
        }
    }
}

@Composable
private fun PanelSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black.copy(alpha = 0.9f),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Black.copy(alpha = 0.58f),
            )
        }
        content()
    }
}

@Composable
private fun OwnerThumbnailCard(
    path: String,
    label: String,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        shadowElevation = 8.dp,
    ) {
        Box(
            modifier = Modifier
                .size(width = 116.dp, height = 132.dp)
                .background(Color.Black.copy(alpha = 0.06f), RoundedCornerShape(18.dp)),
        ) {
            AsyncImage(
                model = File(path),
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.66f),
                            ),
                        ),
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun LensToggleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Color(0xFF0369A1) else Color.Black.copy(alpha = 0.05f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            color = if (selected) Color.White else Color.Black.copy(alpha = 0.74f),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun MetaItem(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black.copy(alpha = 0.55f),
        )
        Text(
            text = value,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Black.copy(alpha = 0.88f),
        )
    }
}

@Composable
private fun EmptyPanelHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.Black.copy(alpha = 0.58f),
    )
}

private fun copyToClipboard(context: Context, value: String) {
    if (value.isBlank()) return
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboardManager.setPrimaryClip(ClipData.newPlainText("hlsUrl", value))
}
