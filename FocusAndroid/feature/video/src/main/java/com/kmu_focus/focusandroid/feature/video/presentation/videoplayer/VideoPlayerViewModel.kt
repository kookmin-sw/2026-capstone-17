package com.kmu_focus.focusandroid.feature.video.presentation.videoplayer

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmu_focus.focusandroid.feature.detection.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.feature.video.data.processor.FrameProcessor
import com.kmu_focus.focusandroid.feature.video.domain.entity.ProcessedFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
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
    // Bitmap 또는 FrameData 중 최신 1장만 보관
    private val latestBitmap = AtomicReference<Bitmap?>(null)
    private val latestFrameData = AtomicReference<FrameData?>(null)

    private data class FrameData(val buffer: ByteBuffer, val width: Int, val height: Int)

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
        latestFrameData.getAndSet(null)
        _uiState.value = _uiState.value.copy(
            isDetecting = false,
            detectedFaces = emptyList()
        )
    }

    fun processFrame(bitmap: Bitmap) {
        if (!_uiState.value.isDetecting) return

        if (!isProcessing.compareAndSet(false, true)) {
            latestBitmap.set(bitmap)
            return
        }

        viewModelScope.launch(defaultDispatcher) {
            try {
                updateUiState(frameProcessor.process(bitmap, System.currentTimeMillis()))
            } finally {
                isProcessing.set(false)
            }

            val pending = latestBitmap.getAndSet(null)
            if (pending != null && _uiState.value.isDetecting) {
                if (isProcessing.compareAndSet(false, true)) {
                    try {
                        updateUiState(frameProcessor.process(pending, System.currentTimeMillis()))
                    } finally {
                        isProcessing.set(false)
                    }
                }
            }
        }
    }

    // GL PBO에서 읽은 ByteBuffer 기반 프레임 처리 (비동기)
    fun processFrame(buffer: ByteBuffer, width: Int, height: Int) {
        if (!_uiState.value.isDetecting) return

        if (!isProcessing.compareAndSet(false, true)) {
            latestFrameData.set(FrameData(buffer, width, height))
            return
        }

        viewModelScope.launch(defaultDispatcher) {
            try {
                updateUiState(frameProcessor.process(buffer, width, height, System.currentTimeMillis()))
            } finally {
                isProcessing.set(false)
            }

            val pending = latestFrameData.getAndSet(null)
            if (pending != null && _uiState.value.isDetecting) {
                if (isProcessing.compareAndSet(false, true)) {
                    try {
                        updateUiState(
                            frameProcessor.process(pending.buffer, pending.width, pending.height, System.currentTimeMillis())
                        )
                    } finally {
                        isProcessing.set(false)
                    }
                }
            }
        }
    }

    // GL 스레드에서 동기 호출: 검출 완료까지 블로킹하여 박스-영상 완벽 동기화
    fun processFrameSync(buffer: ByteBuffer, width: Int, height: Int) {
        if (!_uiState.value.isDetecting) return
        val result = frameProcessor.process(buffer, width, height, System.currentTimeMillis())
        updateUiState(result)
    }

    private fun updateUiState(result: ProcessedFrame) {
        _uiState.value = _uiState.value.copy(
            detectedFaces = result.faces,
            frameWidth = result.frameWidth,
            frameHeight = result.frameHeight
        )
    }
}
