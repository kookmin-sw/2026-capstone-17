package com.kmu_focus.focusandroid.feature.video.presentation.videoplayer

import androidx.lifecycle.ViewModel
import com.kmu_focus.focusandroid.feature.detection.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.feature.video.data.processor.FrameProcessor
import com.kmu_focus.focusandroid.feature.video.domain.entity.ProcessedFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
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
    private val frameProcessor: FrameProcessor
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    private var frameIndexCounter = 0

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
        _uiState.value = _uiState.value.copy(
            isDetecting = false,
            detectedFaces = emptyList()
        )
    }

    // GL 스레드에서 동기 호출: 검출 완료까지 블로킹하여 박스-영상 완벽 동기화
    fun processFrameSync(buffer: ByteBuffer, width: Int, height: Int) {
        if (!_uiState.value.isDetecting) return
        val idx = frameIndexCounter++
        val result = frameProcessor.process(buffer, width, height, System.currentTimeMillis(), idx)
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
