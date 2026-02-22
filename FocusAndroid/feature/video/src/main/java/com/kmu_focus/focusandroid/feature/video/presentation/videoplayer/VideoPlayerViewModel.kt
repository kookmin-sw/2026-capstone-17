package com.kmu_focus.focusandroid.feature.video.presentation.videoplayer

import android.util.Log
import android.view.Surface
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.feature.video.domain.entity.ProcessedFrame
import com.kmu_focus.focusandroid.feature.video.domain.usecase.AddOwnerFromUriUseCase
import com.kmu_focus.focusandroid.feature.video.domain.usecase.PlaybackAnalysisUseCase
import com.kmu_focus.focusandroid.feature.video.domain.usecase.RegisterOwnerDuringPlaybackUseCase
import com.kmu_focus.focusandroid.feature.video.domain.usecase.RecordingUseCase
import com.kmu_focus.focusandroid.feature.video.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject

data class VideoPlayerUiState(
    val videoUri: String = "",
    val isPlaying: Boolean = false,
    val isControlMenuExpanded: Boolean = false,
    val lastRegisteredOwnerImageUri: String? = null,
    val isDetecting: Boolean = false,
    val detectedFaces: List<DetectedFace> = emptyList(),
    val faceLabels: List<Boolean?> = emptyList(),
    val trackingIds: List<Int> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
)

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    private val recordingUseCase: RecordingUseCase,
    private val playbackAnalysisUseCase: PlaybackAnalysisUseCase,
    private val registerOwnerDuringPlaybackUseCase: RegisterOwnerDuringPlaybackUseCase,
    private val addOwnerFromUriUseCase: AddOwnerFromUriUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    private var frameIndexCounter = 0
    private var decodeJob: Job? = null
    @Volatile
    private var latestPositionMs: Long = -1L
    @Volatile
    private var lastGLResult: ProcessedFrame? = null
    @Volatile
    private var labelByTrackId: Map<Int, Boolean?> = emptyMap()
    private val ownerRegistrationLock = Any()
    private val pendingOwnerTrackIds = mutableSetOf<Int>()
    private val pendingOwnerUpgradeTrackIds = mutableSetOf<Int>()
    private val ownerIdByTrackId = mutableMapOf<Int, Int>()
    private val registeringOwnerTrackIds = mutableSetOf<Int>()
    private val upgradingOwnerTrackIds = mutableSetOf<Int>()
    private val lastRegisterAttemptMsByTrackId = mutableMapOf<Int, Long>()
    private val lastUpgradeAttemptMsByTrackId = mutableMapOf<Int, Long>()
    private val savedSnapshotUriByTrackId = mutableMapOf<Int, String>()
    private val snapshotOwnerRegisteredTrackIds = mutableSetOf<Int>()

    @Volatile
    private var encoderSurfaceDispatcher: ((Surface?, Int, Int) -> Unit)? = null

    @Volatile
    private var currentEncoderSurface: Surface? = null

    @Volatile
    private var currentEncoderWidth: Int = 0

    @Volatile
    private var currentEncoderHeight: Int = 0

    var currentRecordingFile: File? = null
        private set

    fun setEncoderSurfaceDispatcher(dispatcher: ((Surface?, Int, Int) -> Unit)?) {
        Log.w(
            TAG,
            "setEncoderSurfaceDispatcher: dispatcherNull=${dispatcher == null}, currentSurfaceNull=${currentEncoderSurface == null}, size=${currentEncoderWidth}x$currentEncoderHeight",
        )
        encoderSurfaceDispatcher = dispatcher
        dispatcher?.invoke(currentEncoderSurface, currentEncoderWidth, currentEncoderHeight)
    }

    fun loadVideo(uri: String) {
        stopDetection()
        _uiState.value = VideoPlayerUiState(videoUri = uri, isPlaying = false)
        viewModelScope.launch(ioDispatcher) {
            val dims = playbackAnalysisUseCase.getVideoDimensions(uri)
            if (dims != null) {
                _uiState.value = _uiState.value.copy(videoWidth = dims.first, videoHeight = dims.second)
            }
        }
    }

    fun togglePlayback() {
        val newIsPlaying = !_uiState.value.isPlaying
        _uiState.value = _uiState.value.copy(
            isPlaying = newIsPlaying,
            isControlMenuExpanded = false,
        )

        if (newIsPlaying) {
            startDetection()
        } else {
            stopDetection()
        }
    }

    fun stopPlayback() {
        stopDetection()
        _uiState.value = _uiState.value.copy(
            isPlaying = false,
            isControlMenuExpanded = false,
        )
    }

    fun toggleControlMenu() {
        _uiState.value = _uiState.value.copy(
            isControlMenuExpanded = !_uiState.value.isControlMenuExpanded,
        )
    }

    fun closeControlMenu() {
        _uiState.value = _uiState.value.copy(isControlMenuExpanded = false)
    }

    fun registerOwnerByTrackId(
        trackId: Int,
        fallbackFaceIndex: Int? = null,
        positionMsHint: Long? = null,
    ) {
        val state = _uiState.value
        if (!state.isPlaying || !state.isDetecting) return
        if (state.videoUri.isEmpty()) return
        val effectivePositionMs = when {
            positionMsHint != null && positionMsHint >= 0L -> {
                latestPositionMs = positionMsHint
                positionMsHint
            }
            latestPositionMs >= 0L -> latestPositionMs
            else -> 0L
        }

        val snapshot = lastGLResult?.let { normalizeTrackingIds(it) }
        val resolvedTrackId = resolveTrackIdForTap(snapshot, trackId, fallbackFaceIndex)
        applyOwnerLabelImmediately(resolvedTrackId, fallbackFaceIndex = fallbackFaceIndex)
        if (snapshot != null) {
            saveFaceSnapshotForTrack(
                uri = state.videoUri,
                positionMs = effectivePositionMs,
                glSnapshot = snapshot,
                trackId = resolvedTrackId,
            )
        }

        synchronized(ownerRegistrationLock) {
            pendingOwnerTrackIds.add(resolvedTrackId)
        }

        tryRegisterPendingOwners(
            glSnapshot = snapshot ?: lastGLResult,
            positionMs = effectivePositionMs,
            uri = state.videoUri,
        )
    }

    private fun startRecording() {
        val w = if (_uiState.value.videoWidth > 0) _uiState.value.videoWidth else 1280
        val h = if (_uiState.value.videoHeight > 0) _uiState.value.videoHeight else 720

        recordingUseCase.startRecording(w, h) { encoderSurface, width, height ->
            currentEncoderSurface = encoderSurface as? Surface
            currentEncoderWidth = width
            currentEncoderHeight = height
            Log.w(TAG, "onInputSurfaceReady: dispatcherNull=${encoderSurfaceDispatcher == null}, size=${width}x$height")
            encoderSurfaceDispatcher?.invoke(currentEncoderSurface, width, height)
        }.fold(
            onSuccess = { file ->
                currentRecordingFile = file
            },
            onFailure = { e ->
                Log.e("VideoPlayerVM", "녹화 시작 실패", e)
                currentRecordingFile = null
                currentEncoderSurface = null
                currentEncoderWidth = 0
                currentEncoderHeight = 0
                encoderSurfaceDispatcher?.invoke(null, 0, 0)
            },
        )
    }

    private fun stopRecording() {
        recordingUseCase.stopRecording()
        currentEncoderSurface = null
        currentEncoderWidth = 0
        currentEncoderHeight = 0
        encoderSurfaceDispatcher?.invoke(null, 0, 0)
    }

    fun startDetection() {
        if (_uiState.value.videoUri.isEmpty()) return
        if (_uiState.value.isDetecting) return
        _uiState.value = _uiState.value.copy(isDetecting = true)
        startRecording()
    }

    fun stopDetection() {
        stopRecording()
        viewModelScope.launch(ioDispatcher) {
            runCatching { playbackAnalysisUseCase.closeMetadataSession() }
                .onFailure { error ->
                    Log.e(TAG, "메타데이터 세션 종료 실패", error)
                }
        }
        lastGLResult = null
        labelByTrackId = emptyMap()
        val snapshotUrisToDelete = synchronized(ownerRegistrationLock) {
            val tempUris = savedSnapshotUriByTrackId.values.toSet().toList()
            pendingOwnerTrackIds.clear()
            pendingOwnerUpgradeTrackIds.clear()
            ownerIdByTrackId.clear()
            registeringOwnerTrackIds.clear()
            upgradingOwnerTrackIds.clear()
            lastRegisterAttemptMsByTrackId.clear()
            lastUpgradeAttemptMsByTrackId.clear()
            savedSnapshotUriByTrackId.clear()
            snapshotOwnerRegisteredTrackIds.clear()
            tempUris
        }
        if (snapshotUrisToDelete.isNotEmpty()) {
            viewModelScope.launch(ioDispatcher) {
                snapshotUrisToDelete.forEach { uri ->
                    registerOwnerDuringPlaybackUseCase.deleteTemporaryFaceSnapshot(uri)
                }
            }
        }
        _uiState.value = _uiState.value.copy(
            isDetecting = false,
            detectedFaces = emptyList(),
            faceLabels = emptyList(),
            trackingIds = emptyList(),
        )
    }

    fun onPlaybackPosition(positionMs: Long) {
        if (!_uiState.value.isDetecting || _uiState.value.videoUri.isEmpty()) return
        latestPositionMs = positionMs
        if (decodeJob?.isActive == true) return
        val uri = _uiState.value.videoUri
        val glSnapshot = lastGLResult
        if (glSnapshot == null || glSnapshot.faces.isEmpty()) return
        decodeJob = viewModelScope.launch {
            val pos = latestPositionMs
            if (pos < 0) return@launch
            val gl = normalizeTrackingIds(lastGLResult ?: return@launch)
            val newLabels = playbackAnalysisUseCase.extractLabelsAtPosition(uri, pos, gl)
            if (newLabels.isNotEmpty()) {
                labelByTrackId = playbackAnalysisUseCase.mergeLabelsWithoutOverwritingOwner(labelByTrackId, newLabels)
                val currentGL = lastGLResult
                if (currentGL != null) {
                    val normalizedCurrent = normalizeTrackingIds(currentGL)
                    val previousLabels = _uiState.value.faceLabels
                    _uiState.value = _uiState.value.copy(
                        faceLabels = normalizedCurrent.faces.indices.map { index ->
                            val tid = normalizedCurrent.trackingIds[index]
                            labelByTrackId[tid] ?: previousLabels.getOrNull(index)
                        },
                    )
                }
            }
            scheduleNextDecode(pos)
        }
    }

    private fun scheduleNextDecode(pos: Long) {
        val nextPos = latestPositionMs
        if (nextPos != pos && nextPos >= 0) {
            viewModelScope.launch { onPlaybackPosition(nextPos) }
        }
    }

    fun processFrameSync(buffer: ByteBuffer, width: Int, height: Int): ProcessedFrame {
        val empty = ProcessedFrame(
            faces = emptyList(),
            frameWidth = width,
            frameHeight = height,
            timestampMs = System.currentTimeMillis(),
        )
        if (!_uiState.value.isDetecting) return empty
        val idx = frameIndexCounter++
        val result = normalizeTrackingIds(
            playbackAnalysisUseCase.processFrame(buffer, width, height, System.currentTimeMillis(), idx)
        )
        lastGLResult = result
        val mergedLabels = result.faces.indices.map { index ->
            val tid = result.trackingIds[index]
            labelByTrackId[tid] ?: result.faceLabels.getOrNull(index)
        }
        _uiState.value = _uiState.value.copy(
            detectedFaces = result.faces,
            faceLabels = mergedLabels,
            trackingIds = result.trackingIds,
            frameWidth = result.frameWidth,
            frameHeight = result.frameHeight,
        )
        tryRegisterPendingOwners(
            glSnapshot = result,
            positionMs = latestPositionMs,
            uri = _uiState.value.videoUri,
        )
        return result.copy(faceLabels = mergedLabels)
    }

    private fun tryRegisterPendingOwners(
        glSnapshot: ProcessedFrame?,
        positionMs: Long,
        uri: String,
    ) {
        if (glSnapshot == null || uri.isEmpty() || positionMs < 0L) return
        val normalizedSnapshot = normalizeTrackingIds(glSnapshot)

        val registerCandidates = synchronized(ownerRegistrationLock) {
            pendingOwnerTrackIds.toList()
        }
        val upgradeCandidates = synchronized(ownerRegistrationLock) {
            pendingOwnerUpgradeTrackIds.toList()
        }
        if (registerCandidates.isEmpty() && upgradeCandidates.isEmpty()) return

        val nowMs = System.currentTimeMillis()
        for (requestedTrackId in registerCandidates) {
            val faceIndex = resolveFaceIndex(normalizedSnapshot, requestedTrackId)
            if (faceIndex < 0) continue
            val resolvedTrackId = normalizedSnapshot.trackingIds[faceIndex]
            val shouldStart = synchronized(ownerRegistrationLock) {
                val inFlight = resolvedTrackId in registeringOwnerTrackIds
                val lastAttempt = lastRegisterAttemptMsByTrackId[resolvedTrackId] ?: Long.MIN_VALUE
                val throttled = nowMs - lastAttempt < OWNER_REGISTER_RETRY_INTERVAL_MS
                if (inFlight || throttled) {
                    false
                } else {
                    registeringOwnerTrackIds.add(resolvedTrackId)
                    lastRegisterAttemptMsByTrackId[resolvedTrackId] = nowMs
                    true
                }
            }
            if (!shouldStart) continue

            viewModelScope.launch(ioDispatcher) {
                val ownerId = runCatching {
                    registerOwnerDuringPlaybackUseCase.registerOwnerAndGetOwnerId(
                        uri = uri,
                        positionMs = positionMs,
                        glResult = normalizedSnapshot,
                        trackId = resolvedTrackId,
                    )
                }.getOrNull()

                if (ownerId != null) {
                    synchronized(ownerRegistrationLock) {
                        pendingOwnerTrackIds.remove(requestedTrackId)
                        pendingOwnerTrackIds.remove(resolvedTrackId)
                        pendingOwnerUpgradeTrackIds.add(resolvedTrackId)
                        ownerIdByTrackId[resolvedTrackId] = ownerId
                        registeringOwnerTrackIds.remove(resolvedTrackId)
                    }
                    applyOwnerLabelImmediately(resolvedTrackId, fallbackFaceIndex = faceIndex)
                } else {
                    val savedSnapshotUri = synchronized(ownerRegistrationLock) {
                        savedSnapshotUriByTrackId[resolvedTrackId]
                    }
                    val fallbackRegistered = savedSnapshotUri?.let { snapshotUri ->
                        runCatching { addOwnerFromUriUseCase(snapshotUri) }.getOrDefault(false)
                    } ?: false
                    synchronized(ownerRegistrationLock) {
                        if (fallbackRegistered) {
                            pendingOwnerTrackIds.remove(requestedTrackId)
                            pendingOwnerTrackIds.remove(resolvedTrackId)
                        }
                        registeringOwnerTrackIds.remove(resolvedTrackId)
                    }
                }
            }
        }

        for (trackId in upgradeCandidates) {
            val ownerId = synchronized(ownerRegistrationLock) { ownerIdByTrackId[trackId] } ?: continue
            val faceIndex = resolveFaceIndex(normalizedSnapshot, trackId)
            if (faceIndex < 0) continue
            val face = normalizedSnapshot.faces.getOrNull(faceIndex) ?: continue
            if (!isFaceGoodForManualOwnerRegistration(face)) continue

            val shouldStart = synchronized(ownerRegistrationLock) {
                val inFlight = trackId in upgradingOwnerTrackIds
                val lastAttempt = lastUpgradeAttemptMsByTrackId[trackId] ?: Long.MIN_VALUE
                val throttled = nowMs - lastAttempt < OWNER_UPGRADE_RETRY_INTERVAL_MS
                if (inFlight || throttled) {
                    false
                } else {
                    upgradingOwnerTrackIds.add(trackId)
                    lastUpgradeAttemptMsByTrackId[trackId] = nowMs
                    true
                }
            }
            if (!shouldStart) continue

            viewModelScope.launch(ioDispatcher) {
                val success = runCatching {
                    registerOwnerDuringPlaybackUseCase.replaceOwnerEmbedding(
                        uri = uri,
                        positionMs = positionMs,
                        glResult = normalizedSnapshot,
                        trackId = trackId,
                        ownerId = ownerId,
                    )
                }.getOrDefault(false)

                synchronized(ownerRegistrationLock) {
                    upgradingOwnerTrackIds.remove(trackId)
                    if (success) {
                        pendingOwnerUpgradeTrackIds.remove(trackId)
                    }
                }
            }
        }
    }

    private fun isFaceGoodForManualOwnerRegistration(face: DetectedFace): Boolean {
        val isFrontal = face.landmarks?.isFrontal(MANUAL_OWNER_FRONTAL_THRESHOLD) == true
        if (!isFrontal) return false
        return face.width >= MANUAL_OWNER_MIN_FACE_SIZE_PX && face.height >= MANUAL_OWNER_MIN_FACE_SIZE_PX
    }

    private fun applyOwnerLabelImmediately(trackId: Int, fallbackFaceIndex: Int? = null) {
        labelByTrackId = labelByTrackId + (trackId to true)
        val currentGL = lastGLResult ?: return
        val normalizedCurrent = normalizeTrackingIds(currentGL)
        val previousLabels = _uiState.value.faceLabels
        _uiState.value = _uiState.value.copy(
            faceLabels = normalizedCurrent.faces.indices.map { index ->
                val tid = normalizedCurrent.trackingIds[index]
                if (tid == trackId) {
                    true
                } else if (fallbackFaceIndex == index) {
                    true
                } else {
                    labelByTrackId[tid] ?: previousLabels.getOrNull(index)
                }
            },
        )
    }

    private fun normalizeTrackingIds(frame: ProcessedFrame): ProcessedFrame {
        if (frame.faces.isEmpty()) return frame.copy(trackingIds = emptyList())
        if (frame.trackingIds.size == frame.faces.size) return frame
        return frame.copy(
            trackingIds = frame.faces.indices.map { index ->
                frame.trackingIds.getOrElse(index) { index }
            }
        )
    }

    private fun resolveFaceIndex(
        frame: ProcessedFrame,
        trackId: Int,
    ): Int {
        val trackedIndex = frame.trackingIds.indexOf(trackId)
        if (trackedIndex >= 0) return trackedIndex
        return trackId.takeIf { it in frame.faces.indices } ?: -1
    }

    private fun resolveTrackIdForTap(
        frame: ProcessedFrame?,
        trackId: Int,
        fallbackFaceIndex: Int?,
    ): Int {
        if (frame == null) return trackId
        if (trackId in frame.trackingIds) return trackId
        val faceIndex = fallbackFaceIndex ?: return trackId
        return frame.trackingIds.getOrElse(faceIndex) { trackId }
    }

    private fun saveFaceSnapshotForTrack(
        uri: String,
        positionMs: Long,
        glSnapshot: ProcessedFrame,
        trackId: Int,
    ) {
        viewModelScope.launch(ioDispatcher) {
            val savedImageUri = runCatching {
                registerOwnerDuringPlaybackUseCase.saveFaceSnapshotTemporarily(
                    uri = uri,
                    positionMs = positionMs,
                    glResult = glSnapshot,
                    trackId = trackId,
                )
            }.getOrNull()
            if (!savedImageUri.isNullOrBlank()) {
                val (shouldRegisterSnapshot, previousSnapshotUri) = synchronized(ownerRegistrationLock) {
                    val previousUri = savedSnapshotUriByTrackId.put(trackId, savedImageUri)
                    (trackId !in snapshotOwnerRegisteredTrackIds) to previousUri
                }
                if (!previousSnapshotUri.isNullOrBlank() && previousSnapshotUri != savedImageUri) {
                    registerOwnerDuringPlaybackUseCase.deleteTemporaryFaceSnapshot(previousSnapshotUri)
                }
                if (shouldRegisterSnapshot) {
                    val registered = runCatching {
                        addOwnerFromUriUseCase(savedImageUri)
                    }.getOrDefault(false)
                    if (registered) {
                        synchronized(ownerRegistrationLock) {
                            snapshotOwnerRegisteredTrackIds.add(trackId)
                            pendingOwnerTrackIds.remove(trackId)
                        }
                    }
                }
                _uiState.value = _uiState.value.copy(
                    lastRegisteredOwnerImageUri = savedImageUri,
                )
            }
        }
    }

    private companion object {
        private const val TAG = "VideoPlayerVM"
        private const val MANUAL_OWNER_MIN_FACE_SIZE_PX = 72
        private const val MANUAL_OWNER_FRONTAL_THRESHOLD = 0.4f
        private const val OWNER_REGISTER_RETRY_INTERVAL_MS = 250L
        private const val OWNER_UPGRADE_RETRY_INTERVAL_MS = 400L
    }
}
