package com.kmu_focus.focusandroid.feature.video.presentation.videoplayer

import android.util.Log
import android.view.Surface
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.feature.video.domain.entity.ProcessedFrame
import com.kmu_focus.focusandroid.feature.video.domain.usecase.PlaybackAnalysisUseCase
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
        _uiState.value = _uiState.value.copy(isPlaying = newIsPlaying)

        if (newIsPlaying) {
            startDetection()
        } else {
            stopDetection()
        }
    }

    fun stopPlayback() {
        stopDetection()
        _uiState.value = _uiState.value.copy(isPlaying = false)
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
        lastGLResult = null
        labelByTrackId = emptyMap()
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
        if (glSnapshot == null || glSnapshot.faces.isEmpty() || glSnapshot.trackingIds.size != glSnapshot.faces.size) return
        decodeJob = viewModelScope.launch {
            val pos = latestPositionMs
            if (pos < 0) return@launch
            val gl = lastGLResult ?: return@launch
            val newLabels = playbackAnalysisUseCase.extractLabelsAtPosition(uri, pos, gl)
            if (newLabels.isNotEmpty()) {
                labelByTrackId = playbackAnalysisUseCase.mergeLabelsWithoutOverwritingOwner(labelByTrackId, newLabels)
                val currentGL = lastGLResult
                if (currentGL != null) {
                    _uiState.value = _uiState.value.copy(
                        faceLabels = currentGL.trackingIds.mapIndexed { i, tid ->
                            labelByTrackId[tid] ?: _uiState.value.faceLabels.getOrNull(i)
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
        val result = playbackAnalysisUseCase.processFrame(buffer, width, height, System.currentTimeMillis(), idx)
        lastGLResult = result
        val mergedLabels = result.trackingIds.mapIndexed { i, tid ->
            labelByTrackId[tid] ?: result.faceLabels.getOrNull(i)
        }
        _uiState.value = _uiState.value.copy(
            detectedFaces = result.faces,
            faceLabels = mergedLabels,
            trackingIds = result.trackingIds,
            frameWidth = result.frameWidth,
            frameHeight = result.frameHeight,
        )
        return result.copy(faceLabels = mergedLabels)
    }

    private companion object {
        private const val TAG = "VideoPlayerVM"
    }
}
