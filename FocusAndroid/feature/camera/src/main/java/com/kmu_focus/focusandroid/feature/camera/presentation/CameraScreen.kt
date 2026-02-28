package com.kmu_focus.focusandroid.feature.camera.presentation

import android.Manifest
import android.app.Activity
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.util.Size
import android.view.Surface
import android.content.pm.ActivityInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.kmu_focus.focusandroid.feature.camera.domain.entity.LensFacing
import com.kmu_focus.focusandroid.core.media.data.gl.VideoGLSurfaceView
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import com.kmu_focus.focusandroid.core.media.presentation.overlay.FaceDetectionOverlay
import com.kmu_focus.focusandroid.core.media.presentation.overlay.FaceTouchOverlay
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executor

@Composable
fun CameraScreen(
    onRecordingComplete: (File) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = context as? Activity
    val isPortraitUi = configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
    val initialRequestedOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    var isLandscapeMode by rememberSaveable {
        mutableStateOf(configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }
    val requiredPermissions = remember {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        )
    }
    var hasAllPermissions by remember {
        mutableStateOf(requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        })
    }
    var glViewRef by remember { mutableStateOf<VideoGLSurfaceView?>(null) }
    var previewAspectRatio by remember { mutableStateOf(DEFAULT_PREVIEW_ASPECT_RATIO) }
    var previewWidth by remember { mutableStateOf(0) }
    var previewHeight by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grantedMap ->
        hasAllPermissions = requiredPermissions.all { permission ->
            grantedMap[permission] == true ||
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    LaunchedEffect(Unit) {
        if (!hasAllPermissions) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    LaunchedEffect(activity, isLandscapeMode) {
        activity?.requestedOrientation = if (isLandscapeMode) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    DisposableEffect(activity, initialRequestedOrientation) {
        onDispose {
            activity?.requestedOrientation = initialRequestedOrientation
        }
    }

    DisposableEffect(hasAllPermissions) {
        if (hasAllPermissions) {
            viewModel.startCamera()
        }
        onDispose {
            if (hasAllPermissions) {
                viewModel.stopCamera()
            }
        }
    }

    DisposableEffect(viewModel, glViewRef) {
        viewModel.setEncoderSurfaceDispatcher { surface, width, height ->
            glViewRef?.setEncoderSurface(surface, width, height)
        }
        onDispose {
            viewModel.setEncoderSurfaceDispatcher(null)
        }
    }

    LaunchedEffect(uiState.recordingFile) {
        val outputFile = uiState.recordingFile ?: return@LaunchedEffect
        onRecordingComplete(outputFile)
        viewModel.clearRecordingFile()
    }

    if (!hasAllPermissions) {
        PermissionRequiredScreen(
            onRequestPermission = { permissionLauncher.launch(requiredPermissions) },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    val (recordingWidth, recordingHeight) = resolveRecordingSize(
        previewWidth = previewWidth,
        previewHeight = previewHeight,
        frameWidth = uiState.frameWidth,
        frameHeight = uiState.frameHeight,
    )
    var isControlMenuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(previewAspectRatio.coerceAtLeast(MIN_PREVIEW_ASPECT_RATIO))
                    .background(Color.Black, RoundedCornerShape(12.dp)),
            ) {
                CameraGLView(
                    lensFacing = uiState.lensFacing,
                    isPortraitUi = isPortraitUi,
                    onFrameCaptured = { buffer, width, height ->
                        viewModel.processFrameSync(buffer, width, height)
                    },
                    onRendererReleased = { viewModel.clearProcessingThreadCache() },
                    onGlSurfaceViewChanged = { glViewRef = it },
                    onPreviewResolutionChanged = { width, height ->
                        if (width > 0 && height > 0) {
                            previewWidth = width
                            previewHeight = height
                            previewAspectRatio = width.toFloat() / height.toFloat()
                        }
                    },
                    onEncoderSurfaceReady = null,
                    modifier = Modifier.fillMaxSize(),
                )

                if (uiState.isDetecting && uiState.detectedFaces.isNotEmpty()) {
                    FaceDetectionOverlay(
                        faces = uiState.detectedFaces,
                        frameWidth = uiState.frameWidth,
                        frameHeight = uiState.frameHeight,
                        faceLabels = uiState.faceLabels,
                        trackingIds = uiState.trackingIds,
                        modifier = Modifier.fillMaxSize(),
                    )
                    FaceTouchOverlay(
                        faces = uiState.detectedFaces,
                        trackingIds = uiState.trackingIds,
                        frameWidth = uiState.frameWidth,
                        frameHeight = uiState.frameHeight,
                        onFaceTapped = { trackId, faceIndex ->
                            viewModel.registerOwnerByTrackId(trackId, faceIndex)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        ) {
            IconButton(
                onClick = { isControlMenuExpanded = !isControlMenuExpanded },
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(999.dp)),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "카메라 메뉴",
                    tint = Color.White,
                )
            }

            DropdownMenu(
                expanded = isControlMenuExpanded,
                onDismissRequest = { isControlMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(if (uiState.isDetecting) "검출 중지" else "검출 시작") },
                    onClick = {
                        if (uiState.isDetecting) viewModel.stopDetection() else viewModel.startDetection()
                        isControlMenuExpanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (uiState.isRecording) "녹화 중지" else "녹화 시작") },
                    enabled = uiState.isDetecting || uiState.isRecording,
                    onClick = {
                        if (uiState.isRecording) {
                            viewModel.stopRecording()
                        } else {
                            viewModel.startRecording(recordingWidth, recordingHeight)
                        }
                        isControlMenuExpanded = false
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (uiState.lensFacing == LensFacing.BACK) {
                                "전면 카메라로 전환"
                            } else {
                                "후면 카메라로 전환"
                            },
                        )
                    },
                    onClick = {
                        viewModel.switchLensFacing()
                        isControlMenuExpanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (isLandscapeMode) "세로 모드로 전환" else "가로 모드로 전환") },
                    enabled = !uiState.isRecording,
                    onClick = {
                        if (uiState.isRecording) return@DropdownMenuItem
                        isLandscapeMode = !isLandscapeMode
                        isControlMenuExpanded = false
                    },
                )
                if (onBack != null) {
                    DropdownMenuItem(
                        text = { Text("모드 선택으로 돌아가기") },
                        onClick = {
                            isControlMenuExpanded = false
                            onBack()
                        },
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "탭으로 OWNER 지정",
                color = Color.White,
            )
            Text(
                text = "렌즈: ${if (uiState.lensFacing == LensFacing.BACK) "후면" else "전면"}",
                color = Color.White,
            )
            Text(
                text = "검출: ${if (uiState.isDetecting) "ON" else "OFF"} · 녹화: ${if (uiState.isRecording) "ON" else "OFF"}",
                color = Color.White,
            )
        }
    }
}

@Composable
private fun PermissionRequiredScreen(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("카메라/마이크 권한이 필요합니다.")
            Spacer(modifier = Modifier.size(8.dp))
            Button(onClick = onRequestPermission) {
                Text("권한 요청")
            }
        }
    }
}

@Composable
private fun CameraGLView(
    lensFacing: LensFacing,
    isPortraitUi: Boolean,
    onFrameCaptured: (ByteBuffer, Int, Int) -> ProcessedFrame?,
    onRendererReleased: () -> Unit,
    onGlSurfaceViewChanged: (VideoGLSurfaceView?) -> Unit,
    onPreviewResolutionChanged: (Int, Int) -> Unit = { _, _ -> },
    onEncoderSurfaceReady: ((Surface, Int, Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val cameraProviderFuture = remember(context) { ProcessCameraProvider.getInstance(context) }

    var previewSurface by remember { mutableStateOf<Surface?>(null) }
    var glSurfaceView by remember { mutableStateOf<VideoGLSurfaceView?>(null) }

    DisposableEffect(lensFacing, isPortraitUi, lifecycleOwner, previewSurface, glSurfaceView) {
        val surface = previewSurface
        val view = glSurfaceView
        if (surface == null || view == null) {
            onDispose { }
        } else {
            val fallbackRotation = if (isPortraitUi) Surface.ROTATION_0 else Surface.ROTATION_90
            val initialDisplayRotation = view.display?.rotation ?: fallbackRotation
            view.setPreviewRotationDegrees(rotationToDegrees(initialDisplayRotation))

            val previewUseCase = Preview.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(
                            AspectRatioStrategy(
                                AspectRatio.RATIO_16_9,
                                AspectRatioStrategy.FALLBACK_RULE_AUTO,
                            ),
                        )
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(PREFERRED_PREVIEW_WIDTH, PREFERRED_PREVIEW_HEIGHT),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            ),
                        )
                        .build(),
                )
                .build()
            previewUseCase.targetRotation = initialDisplayRotation
            val selector = CameraSelector.Builder()
                .requireLensFacing(
                    if (lensFacing == LensFacing.FRONT) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    },
                )
                .build()

            cameraProviderFuture.addListener(
                {
                    val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull() ?: return@addListener
                    runCatching {
                        cameraProvider.unbindAll()
                        previewUseCase.setSurfaceProvider { request ->
                            // 실제 신규 카메라 스트림 요청 시점에 렌즈 방향 보정을 갱신한다.
                            // 전환 중 잔여 프레임에 새 보정이 적용되며 뒤집혀 보이는 현상을 방지한다.
                            view.setFrontLensFacing(lensFacing == LensFacing.FRONT)
                            val currentDisplayRotation = view.display?.rotation ?: fallbackRotation
                            previewUseCase.targetRotation = currentDisplayRotation
                            view.setPreviewRotationDegrees(rotationToDegrees(currentDisplayRotation))
                            val resolution = request.resolution
                            view.setInputSurfaceSize(resolution.width, resolution.height)
                            val normalizedSize = normalizeResolutionForUi(
                                width = resolution.width,
                                height = resolution.height,
                                isPortraitUi = isPortraitUi,
                            )
                            onPreviewResolutionChanged(normalizedSize.first, normalizedSize.second)
                            view.setVideoSize(normalizedSize.first, normalizedSize.second)
                            request.provideSurface(surface, mainExecutor) { }
                        }
                        cameraProvider.bindToLifecycle(lifecycleOwner, selector, previewUseCase)
                    }
                },
                mainExecutor,
            )

            onDispose {
                unbindCameraProvider(cameraProviderFuture, mainExecutor)
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            VideoGLSurfaceView(
                context = ctx,
                onFrameCaptured = { buffer, width, height ->
                    onFrameCaptured(buffer, width, height) ?: ProcessedFrame(
                        faces = emptyList(),
                        frameWidth = width,
                        frameHeight = height,
                        timestampMs = System.currentTimeMillis(),
                    )
                },
                onSurfaceReady = { surface ->
                    previewSurface = surface
                },
                onRendererReleased = onRendererReleased,
            ).also { createdView ->
                glSurfaceView = createdView
                onGlSurfaceViewChanged(createdView)
            }
        },
        onRelease = { releasedView ->
            (releasedView as? VideoGLSurfaceView)?.setEncoderSurface(null, 0, 0)
            onGlSurfaceViewChanged(null)
            glSurfaceView = null
            previewSurface = null
            unbindCameraProvider(cameraProviderFuture, mainExecutor)
            (releasedView as? VideoGLSurfaceView)?.release()
        },
        modifier = modifier,
    )

    // 시그니처 유지용: 인코더 surface 연결은 ViewModel dispatcher를 통해 수행한다.
    if (onEncoderSurfaceReady != null) {
        // no-op
    }
}

private fun unbindCameraProvider(
    future: ListenableFuture<ProcessCameraProvider>,
    executor: Executor,
) {
    future.addListener(
        {
            runCatching { future.get().unbindAll() }
        },
        executor,
    )
}

private const val DEFAULT_RECORDING_WIDTH = 1280
private const val DEFAULT_RECORDING_HEIGHT = 720
private const val DEFAULT_PREVIEW_ASPECT_RATIO = 4f / 3f
private const val MIN_PREVIEW_ASPECT_RATIO = 0.5f
private const val PREFERRED_PREVIEW_WIDTH = 1920
private const val PREFERRED_PREVIEW_HEIGHT = 1080

private fun normalizeResolutionForUi(
    width: Int,
    height: Int,
    isPortraitUi: Boolean,
): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return width to height
    val isLandscapeBuffer = width >= height
    val shouldSwap = (isPortraitUi && isLandscapeBuffer) || (!isPortraitUi && !isLandscapeBuffer)
    return if (shouldSwap) height to width else width to height
}

private fun resolveRecordingSize(
    previewWidth: Int,
    previewHeight: Int,
    frameWidth: Int,
    frameHeight: Int,
): Pair<Int, Int> {
    val rawWidth = when {
        previewWidth > 0 -> previewWidth
        frameWidth > 0 -> frameWidth
        else -> DEFAULT_RECORDING_WIDTH
    }
    val rawHeight = when {
        previewHeight > 0 -> previewHeight
        frameHeight > 0 -> frameHeight
        else -> DEFAULT_RECORDING_HEIGHT
    }
    return normalizeRecordingSize(rawWidth, rawHeight)
}

private fun normalizeRecordingSize(width: Int, height: Int): Pair<Int, Int> {
    val safeWidth = width.coerceAtLeast(2)
    val safeHeight = height.coerceAtLeast(2)
    val alignedWidth = if (safeWidth % 2 == 0) safeWidth else safeWidth - 1
    val alignedHeight = if (safeHeight % 2 == 0) safeHeight else safeHeight - 1
    return alignedWidth.coerceAtLeast(2) to alignedHeight.coerceAtLeast(2)
}

private fun rotationToDegrees(rotation: Int): Int = when (rotation) {
    Surface.ROTATION_90 -> 90
    Surface.ROTATION_180 -> 180
    Surface.ROTATION_270 -> 270
    else -> 0
}
