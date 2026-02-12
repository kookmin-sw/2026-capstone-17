package com.kmu_focus.focusandroid.feature.video.presentation.videoplayer

import android.graphics.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmu_focus.focusandroid.core.ai.data.recognition.FaceAlignment
import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.OwnerOtherClassifier
import com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.core.ai.domain.entity.FaceLandmarks5
import com.kmu_focus.focusandroid.core.ai.domain.entity.Point2f
import com.kmu_focus.focusandroid.core.ai.data.recognition.ArcFaceEmbeddingExtractor
import com.kmu_focus.focusandroid.feature.video.data.decoder.VideoFrameDecoder
import com.kmu_focus.focusandroid.feature.video.data.processor.FrameProcessor
import com.kmu_focus.focusandroid.feature.video.domain.entity.ProcessedFrame
import com.kmu_focus.focusandroid.feature.video.data.recorder.RealTimeRecorder
import com.kmu_focus.focusandroid.feature.video.data.local.VideoLocalDataSource
import com.kmu_focus.focusandroid.feature.video.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
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
    val encoderSurface: android.view.Surface? = null
)

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    private val frameProcessor: FrameProcessor,
    private val videoFrameDecoder: VideoFrameDecoder,
    private val embeddingExtractor: ArcFaceEmbeddingExtractor,
    private val ownerClassifier: OwnerOtherClassifier,
    private val realTimeRecorder: RealTimeRecorder,
    private val videoLocalDataSource: VideoLocalDataSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
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
    
    // 녹화 중인 임시 파일
    var currentRecordingFile: File? = null
        private set

    fun loadVideo(uri: String) {
        stopDetection()
        _uiState.value = VideoPlayerUiState(videoUri = uri, isPlaying = false)
        viewModelScope.launch(ioDispatcher) {
            val dims = videoFrameDecoder.getVideoDimensions(uri)
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
        stopRecording()
        _uiState.value = _uiState.value.copy(isPlaying = false)
    }

    private fun startRecording() {
        try {
            val file = videoLocalDataSource.createTempOutputFile()
            currentRecordingFile = file
            
            // 영상 해상도가 있으면 그 해상도로, 없으면 기본값 (예: 720p)
            val w = if (uiState.value.videoWidth > 0) uiState.value.videoWidth else 1280
            val h = if (uiState.value.videoHeight > 0) uiState.value.videoHeight else 720
            
            realTimeRecorder.start(
                 width = w,
                 height = h,
                 outputFile = file,
                 onInputSurfaceReady = { surface ->
                     _uiState.update { it.copy(encoderSurface = surface) }
                 }
            )
        } catch (e: Exception) {
            android.util.Log.e("VideoPlayerVM", "녹화 시작 실패", e)
        }
    }

    private fun stopRecording() {
        try {
            realTimeRecorder.stop()
            _uiState.update { it.copy(encoderSurface = null) }
        } catch (e: Exception) {
            android.util.Log.e("VideoPlayerVM", "녹화 중지 실패", e)
        }
    }

    fun startDetection() {
        if (_uiState.value.videoUri.isEmpty()) return
        if (_uiState.value.isDetecting) return
        _uiState.value = _uiState.value.copy(isDetecting = true)
        
        // 탐지 시작 시 녹화도 같이 시작
        startRecording()
    }

    fun stopDetection() {
        lastGLResult = null
        labelByTrackId = emptyMap()
        _uiState.value = _uiState.value.copy(
            isDetecting = false,
            detectedFaces = emptyList(),
            faceLabels = emptyList(),
            trackingIds = emptyList()
        )
    }

    /** 원본 해상도 프레임에서 해당 구역만 잘라 Owner/Other 라벨만 추출 → GL 박스에 반영. */
    fun onPlaybackPosition(positionMs: Long) {
        if (!_uiState.value.isDetecting || _uiState.value.videoUri.isEmpty()) return
        latestPositionMs = positionMs
        if (decodeJob?.isActive == true) return
        val uri = _uiState.value.videoUri
        val glSnapshot = lastGLResult
        decodeJob = viewModelScope.launch {
            val pos = latestPositionMs
            if (pos < 0) return@launch
            val bitmap = withContext(Dispatchers.IO) {
                videoFrameDecoder.decodeFrameAt(uri, pos)
            } ?: run {
                scheduleNextDecode(pos)
                return@launch
            }
            val meta = withContext(Dispatchers.IO) { videoFrameDecoder.getVideoDimensions(uri) }
            if (meta != null && (meta.first != bitmap.width || meta.second != bitmap.height)) {
                android.util.Log.w("VideoPlayerVM", "디코드 해상도 불일치: 영상 메타 ${meta.first}x${meta.second}, getFrameAtTime 반환 ${bitmap.width}x${bitmap.height}")
            }
            try {
                val gl = glSnapshot ?: lastGLResult
                if (gl != null && gl.faces.isNotEmpty() && gl.trackingIds.size == gl.faces.size) {
                    val newLabels = withContext(Dispatchers.IO) {
                        extractLabelsFromOriginalFrame(bitmap, gl)
                    }
                    if (newLabels.isNotEmpty()) {
                        labelByTrackId = mergeLabelsWithoutOverwritingOwner(labelByTrackId, newLabels)
                        val currentGL = lastGLResult
                        val currentLabels = _uiState.value.faceLabels
                        if (currentGL != null) {
                            _uiState.value = _uiState.value.copy(
                                faceLabels = currentGL.trackingIds.mapIndexed { i, tid ->
                                    labelByTrackId[tid] ?: currentLabels.getOrNull(i)
                                }
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("VideoPlayerVM", "extract labels failed: ${e.message}", e)
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            scheduleNextDecode(pos)
        }
    }

    /** OWNER 확정 후 OTHER로 바꾸지 않음. 디코드 경로는 단일 프레임이라 OTHER 노이즈 많음 → 디코드에서는 OWNER만 반영, OTHER는 GL(3프레임) 결과만 사용. */
    private fun mergeLabelsWithoutOverwritingOwner(current: Map<Int, Boolean?>, newLabels: Map<Int, Boolean?>): Map<Int, Boolean?> {
        val out = current.toMutableMap()
        for ((tid, newVal) in newLabels) {
            when (out[tid]) {
                true -> { } // OWNER 유지
                else -> {
                    if (newVal == true) out[tid] = true  // OWNER만 디코드에서 반영
                    // newVal == false(OTHER)는 디코드에서 넣지 않음 → GL 경로(3프레임) 결과 사용
                }
            }
        }
        return out
    }

    private fun scheduleNextDecode(pos: Long) {
        val nextPos = latestPositionMs
        if (nextPos != pos && nextPos >= 0) {
            viewModelScope.launch { onPlaybackPosition(nextPos) }
        }
    }

    /**
     * 원본 비트맵에서 GL bbox에 대응하는 구역만 잘라 ArcFace + Owner/Other 판별.
     * GL 버퍼는 view 크기(2196×990 등)이고 영상은 fit(letter-box)로 그려지므로,
     * content rect 기준으로 GL 좌표 → 원본 픽셀 변환.
     */
    private fun extractLabelsFromOriginalFrame(originalBitmap: android.graphics.Bitmap, glResult: ProcessedFrame): Map<Int, Boolean?> {
        val out = mutableMapOf<Int, Boolean?>()
        val gw = glResult.frameWidth.coerceAtLeast(1)
        val gh = glResult.frameHeight.coerceAtLeast(1)
        val ow = originalBitmap.width
        val oh = originalBitmap.height
        // GL 버퍼 내 영상 content rect (fit, 중앙 정렬 가정)
        val scale = minOf(gw / ow.toFloat(), gh / oh.toFloat())
        val contentW = ow * scale
        val contentH = oh * scale
        val contentLeft = (gw - contentW) / 2f
        val contentTop = (gh - contentH) / 2f
        // GL 좌표 → 원본: (x - contentLeft) / scale 등
        for (i in glResult.faces.indices) {
            val face = glResult.faces[i]
            val trackId = glResult.trackingIds.getOrNull(i) ?: i
            val lm = face.landmarks ?: continue
            val decodedLeft = ((face.x - contentLeft) / scale).toInt().coerceIn(0, ow - 1)
            val decodedTop = ((face.y - contentTop) / scale).toInt().coerceIn(0, oh - 1)
            val decodedW = (face.width / scale).toInt().coerceIn(1, ow - decodedLeft)
            val decodedH = (face.height / scale).toInt().coerceIn(1, oh - decodedTop)
            val crop = android.graphics.Bitmap.createBitmap(originalBitmap, decodedLeft, decodedTop, decodedW, decodedH)
            if (trackId in setOf(15, 31, 38, 39, 43)) {
                android.util.Log.i("VideoPlayerVM", "ID:$trackId 원본 ${ow}x${oh} (GL ${gw}x${gh} content scale $scale) | crop ${crop.width}x${crop.height}")
            }
            try {
                val cropRect = Rect(0, 0, crop.width, crop.height)
                val landmarksInCrop = FaceLandmarks5(
                    rightEye = Point2f((lm.rightEye.x - face.x) / scale, (lm.rightEye.y - face.y) / scale),
                    leftEye = Point2f((lm.leftEye.x - face.x) / scale, (lm.leftEye.y - face.y) / scale),
                    nose = Point2f((lm.nose.x - face.x) / scale, (lm.nose.y - face.y) / scale),
                    rightMouth = Point2f((lm.rightMouth.x - face.x) / scale, (lm.rightMouth.y - face.y) / scale),
                    leftMouth = Point2f((lm.leftMouth.x - face.x) / scale, (lm.leftMouth.y - face.y) / scale)
                )
                val aligned = FaceAlignment.alignFaceForRecognition(crop, landmarksInCrop, cropRect)
                if (aligned != crop) crop.recycle()
                try {
                    val emb = embeddingExtractor.extractEmbedding(aligned) ?: continue
                    val (isOwner, _) = ownerClassifier.decideLabel(listOf(emb))
                    out[trackId] = isOwner
                } finally {
                    if (!aligned.isRecycled) aligned.recycle()
                }
            } finally {
                if (!crop.isRecycled) crop.recycle()
            }
        }
        return out
    }

    /** GL 경로: YuNet + 3DMM + 트래킹(위치). 라벨은 원본 구역 디코드 결과로 보정. */
    fun processFrameSync(buffer: ByteBuffer, width: Int, height: Int) {
        if (!_uiState.value.isDetecting) return
        val idx = frameIndexCounter++
        val result = frameProcessor.process(buffer, width, height, System.currentTimeMillis(), idx)
        lastGLResult = result
        val mergedLabels = result.trackingIds.mapIndexed { i, tid ->
            labelByTrackId[tid] ?: result.faceLabels.getOrNull(i)
        }
        _uiState.value = _uiState.value.copy(
            detectedFaces = result.faces,
            faceLabels = mergedLabels,
            trackingIds = result.trackingIds,
            frameWidth = result.frameWidth,
            frameHeight = result.frameHeight
        )
    }
}
