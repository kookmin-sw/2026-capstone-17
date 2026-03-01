package com.kmu_focus.focusandroid.feature.camera.presentation

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.feature.camera.domain.entity.LensFacing
import com.kmu_focus.focusandroid.feature.camera.domain.usecase.CameraAnalysisUseCase
import com.kmu_focus.focusandroid.feature.camera.domain.usecase.CameraRecordingUseCase
import com.kmu_focus.focusandroid.core.media.di.IoDispatcher
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class CameraUiState(
    val isCameraActive: Boolean = false,
    val isDetecting: Boolean = false,
    val isRecording: Boolean = false,
    val lensFacing: LensFacing = LensFacing.BACK,
    val detectedFaces: List<DetectedFace> = emptyList(),
    val faceLabels: List<Boolean?> = emptyList(),
    val trackingIds: List<Int> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val recordingFile: File? = null,
    val registeredOwnerThumbnails: List<String> = emptyList(),
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val cameraAnalysisUseCase: CameraAnalysisUseCase,
    private val cameraRecordingUseCase: CameraRecordingUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    @Volatile
    private var encoderSurfaceDispatcher: ((Surface?, Int, Int) -> Unit)? = null

    @Volatile
    private var currentEncoderSurface: Surface? = null

    @Volatile
    private var currentEncoderWidth: Int = 0

    @Volatile
    private var currentEncoderHeight: Int = 0

    @Volatile
    private var currentRecordingFile: File? = null

    private val manualOwnerTrackIds = linkedSetOf<Int>()

    @Volatile
    private var pendingOwnerRegistrationTrackId: Int? = null

    fun setEncoderSurfaceDispatcher(dispatcher: ((Surface?, Int, Int) -> Unit)?) {
        encoderSurfaceDispatcher = dispatcher
        dispatcher?.invoke(currentEncoderSurface, currentEncoderWidth, currentEncoderHeight)
    }

    fun startCamera() {
        if (_uiState.value.isCameraActive) return
        _uiState.value = _uiState.value.copy(isCameraActive = true)
    }

    fun stopCamera() {
        stopRecordingInternal(saveRecordingFile = false)
        manualOwnerTrackIds.clear()
        pendingOwnerRegistrationTrackId = null
        _uiState.value = _uiState.value.copy(
            isCameraActive = false,
            isDetecting = false,
            isRecording = false,
            detectedFaces = emptyList(),
            faceLabels = emptyList(),
            trackingIds = emptyList(),
            registeredOwnerThumbnails = emptyList(),
        )
        cameraAnalysisUseCase.clearProcessingThreadCache()
    }

    fun startDetection() {
        if (!_uiState.value.isCameraActive) return
        if (_uiState.value.isDetecting) return
        _uiState.value = _uiState.value.copy(isDetecting = true)
    }

    fun stopDetection() {
        stopRecordingInternal(saveRecordingFile = false)
        manualOwnerTrackIds.clear()
        pendingOwnerRegistrationTrackId = null
        _uiState.value = _uiState.value.copy(
            isDetecting = false,
            isRecording = false,
            detectedFaces = emptyList(),
            faceLabels = emptyList(),
            trackingIds = emptyList(),
            registeredOwnerThumbnails = emptyList(),
        )
    }

    fun processFrameSync(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
    ): ProcessedFrame? {
        val currentState = _uiState.value
        if (!currentState.isCameraActive || !currentState.isDetecting) return null

        val result = cameraAnalysisUseCase.processFrame(
            rgbaBuffer = buffer,
            width = width,
            height = height,
            timestampMs = System.currentTimeMillis(),
        )

        val pendingTrackId = pendingOwnerRegistrationTrackId
        if (pendingTrackId != null && result.trackingIds.contains(pendingTrackId)) {
            pendingOwnerRegistrationTrackId = null
            val regResult = cameraAnalysisUseCase.registerOwnerFromFrame(
                rgbaBuffer = buffer,
                width = width,
                height = height,
                trackId = pendingTrackId,
                processedFrame = result,
            )
            if (regResult.success) {
                manualOwnerTrackIds.add(pendingTrackId)
                regResult.thumbnailPath?.let { path ->
                    _uiState.value = _uiState.value.copy(
                        registeredOwnerThumbnails = _uiState.value.registeredOwnerThumbnails + path,
                    )
                }
            }
        }

        val mergedLabels = result.faces.indices.map { index ->
            val trackId = result.trackingIds.getOrNull(index) ?: index
            if (trackId in manualOwnerTrackIds) {
                true
            } else {
                result.faceLabels.getOrNull(index)
            }
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

    fun startRecording(width: Int, height: Int) {
        val currentState = _uiState.value
        if (!currentState.isCameraActive || !currentState.isDetecting || currentState.isRecording) return

        viewModelScope.launch(ioDispatcher) {
            cameraAnalysisUseCase.startMetadataSession()
            val startResult = cameraRecordingUseCase.startRecording(
                width = width,
                height = height,
                onSurfaceReady = { encoderSurface, targetWidth, targetHeight ->
                    currentEncoderSurface = encoderSurface as? Surface
                    currentEncoderWidth = targetWidth
                    currentEncoderHeight = targetHeight
                    encoderSurfaceDispatcher?.invoke(
                        currentEncoderSurface,
                        currentEncoderWidth,
                        currentEncoderHeight,
                    )
                },
            )
            startResult.fold(
                onSuccess = { file ->
                    currentRecordingFile = file
                    _uiState.value = _uiState.value.copy(isRecording = true)
                },
                onFailure = {
                    currentRecordingFile = null
                    clearEncoderSurface()
                    _uiState.value = _uiState.value.copy(isRecording = false)
                },
            )
            if (startResult.isFailure) {
                cameraAnalysisUseCase.closeMetadataSession()
            }
        }
    }

    fun stopRecording() {
        stopRecordingInternal(saveRecordingFile = true)
    }

    fun switchLensFacing() {
        manualOwnerTrackIds.clear()
        pendingOwnerRegistrationTrackId = null

        val nextLens = when (_uiState.value.lensFacing) {
            LensFacing.FRONT -> LensFacing.BACK
            LensFacing.BACK -> LensFacing.FRONT
        }
        _uiState.value = _uiState.value.copy(
            lensFacing = nextLens,
            detectedFaces = emptyList(),
            faceLabels = emptyList(),
            trackingIds = emptyList(),
            registeredOwnerThumbnails = emptyList(),
        )
        cameraAnalysisUseCase.clearProcessingThreadCache()
    }

    fun registerOwnerByTrackId(
        trackId: Int,
        fallbackFaceIndex: Int? = null,
    ) {
        val state = _uiState.value
        if (!state.isCameraActive || !state.isDetecting) return
        val resolvedTrackId = resolveTrackId(trackId, fallbackFaceIndex, state.trackingIds)

        pendingOwnerRegistrationTrackId = resolvedTrackId

        _uiState.value = state.copy(
            faceLabels = state.faceLabels.mapIndexed { index, currentLabel ->
                val currentTrackId = state.trackingIds.getOrNull(index) ?: index
                if (currentTrackId == resolvedTrackId || fallbackFaceIndex == index) {
                    true
                } else {
                    currentLabel
                }
            },
        )
    }

    fun clearRecordingFile() {
        _uiState.value = _uiState.value.copy(recordingFile = null)
    }

    fun clearProcessingThreadCache() {
        cameraAnalysisUseCase.clearProcessingThreadCache()
    }

    override fun onCleared() {
        try {
            stopRecordingInternal(saveRecordingFile = false, forceSynchronous = true)
            cameraAnalysisUseCase.clearProcessingThreadCache()
        } finally {
            super.onCleared()
        }
    }

    private fun stopRecordingInternal(
        saveRecordingFile: Boolean,
        forceSynchronous: Boolean = false,
    ) {
        val wasRecording = _uiState.value.isRecording
        val fileToEmit = if (saveRecordingFile) currentRecordingFile else null

        _uiState.value = _uiState.value.copy(isRecording = false)
        clearEncoderSurface()

        if (!wasRecording) {
            if (!saveRecordingFile) {
                currentRecordingFile = null
            }
            return
        }

        val stopAction: suspend () -> Unit = {
            cameraRecordingUseCase.stopRecording()
            cameraAnalysisUseCase.closeMetadataSession()
            currentRecordingFile = null
            if (fileToEmit != null) {
                _uiState.value = _uiState.value.copy(recordingFile = fileToEmit)
            }
        }

        if (forceSynchronous) {
            runBlocking {
                withContext(ioDispatcher + NonCancellable) {
                    stopAction()
                }
            }
        } else {
            viewModelScope.launch(ioDispatcher) {
                stopAction()
            }
        }
    }

    private fun clearEncoderSurface() {
        currentEncoderSurface = null
        currentEncoderWidth = 0
        currentEncoderHeight = 0
        encoderSurfaceDispatcher?.invoke(null, 0, 0)
    }

    private fun resolveTrackId(
        trackId: Int,
        fallbackFaceIndex: Int?,
        trackingIds: List<Int>,
    ): Int {
        if (trackId in trackingIds) return trackId
        val fallbackIndex = fallbackFaceIndex ?: return trackId
        return trackingIds.getOrElse(fallbackIndex) { trackId }
    }
}
