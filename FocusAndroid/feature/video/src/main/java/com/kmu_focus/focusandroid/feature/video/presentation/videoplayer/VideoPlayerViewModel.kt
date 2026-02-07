package com.kmu_focus.focusandroid.feature.video.presentation.videoplayer

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmu_focus.focusandroid.feature.detection.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.feature.video.data.processor.FrameProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

data class VideoPlayerUiState(
    val videoUri: String = "",
    val isPlaying: Boolean = false,
    val isDetecting: Boolean = false,
    val detectedFaces: List<DetectedFace> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0
)

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    private val frameProcessor: FrameProcessor,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    private val isProcessing = AtomicBoolean(false)
    // 처리 중 도착한 최신 프레임 1장만 보관
    private val latestBitmap = AtomicReference<Bitmap?>(null)

    fun loadVideo(uri: String) {
        stopDetection()
        _uiState.value = VideoPlayerUiState(videoUri = uri, isPlaying = false)
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

    fun startDetection() {
        if (_uiState.value.videoUri.isEmpty()) return
        if (_uiState.value.isDetecting) return
        _uiState.value = _uiState.value.copy(isDetecting = true)
    }

    fun stopDetection() {
        isProcessing.set(false)
        latestBitmap.getAndSet(null)
        _uiState.value = _uiState.value.copy(
            isDetecting = false,
            detectedFaces = emptyList()
        )
    }

    fun processFrame(bitmap: Bitmap) {
        if (!_uiState.value.isDetecting) return

        // 이전 검출 진행 중이면 최신 비트맵만 보관하고 return (이전 결과 유지)
        if (!isProcessing.compareAndSet(false, true)) {
            latestBitmap.set(bitmap)
            return
        }

        viewModelScope.launch(defaultDispatcher) {
            try {
                processFrameInternal(bitmap)
            } finally {
                isProcessing.set(false)
            }

            // 처리 완료 후 대기 중인 최신 프레임이 있으면 즉시 처리
            val pending = latestBitmap.getAndSet(null)
            if (pending != null && _uiState.value.isDetecting) {
                if (isProcessing.compareAndSet(false, true)) {
                    try {
                        processFrameInternal(pending)
                    } finally {
                        isProcessing.set(false)
                    }
                }
            }
        }
    }

    private fun processFrameInternal(bitmap: Bitmap) {
        val result = frameProcessor.process(bitmap, System.currentTimeMillis())
        _uiState.value = _uiState.value.copy(
            detectedFaces = result.faces,
            frameWidth = result.frameWidth,
            frameHeight = result.frameHeight
        )
    }
}
