package com.kmu_focus.focusandroid.feature.broadcast.presentation.camera

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmu_focus.focusandroid.core.media.data.recorder.RealTimeRecorder
import com.kmu_focus.focusandroid.core.streaming.domain.entity.SrtConnectionState
import com.kmu_focus.focusandroid.feature.broadcast.BuildConfig
import com.kmu_focus.focusandroid.feature.broadcast.domain.usecase.BroadcastStreamingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val BROADCAST_ID_KEY = "broadcastId"
private const val STREAM_KEY_KEY = "streamKey"
private const val HLS_URL_KEY = "hlsUrl"

data class BroadcastCameraUiState(
    val broadcastId: String = "",
    val streamKey: String = "",
    val hlsUrl: String = "",
    val srtState: SrtConnectionState = SrtConnectionState.DISCONNECTED,
    val isPreparing: Boolean = false,
    val isBroadcasting: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class BroadcastCameraViewModel @Inject constructor(
    private val broadcastStreamingUseCase: BroadcastStreamingUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    constructor(
        broadcastStreamingUseCase: BroadcastStreamingUseCase,
        broadcastId: String,
        streamKey: String,
    ) : this(
        broadcastStreamingUseCase = broadcastStreamingUseCase,
        savedStateHandle = SavedStateHandle(
            mapOf(
                BROADCAST_ID_KEY to broadcastId,
                STREAM_KEY_KEY to streamKey,
            ),
        ),
    )

    private val _uiState = MutableStateFlow(
        BroadcastCameraUiState(
            broadcastId = savedStateHandle[BROADCAST_ID_KEY] ?: "",
            streamKey = savedStateHandle[STREAM_KEY_KEY] ?: "",
            hlsUrl = savedStateHandle[HLS_URL_KEY] ?: "",
        ),
    )
    val uiState: StateFlow<BroadcastCameraUiState> = _uiState.asStateFlow()

    private var heartbeatJob: Job? = null

    val currentMuxerFactory: RealTimeRecorder.VideoMuxerFactory?
        get() = broadcastStreamingUseCase.currentMuxerFactory

    fun updateSession(
        broadcastId: String,
        streamKey: String,
        hlsUrl: String = uiState.value.hlsUrl,
    ) {
        savedStateHandle[BROADCAST_ID_KEY] = broadcastId
        savedStateHandle[STREAM_KEY_KEY] = streamKey
        savedStateHandle[HLS_URL_KEY] = hlsUrl
        _uiState.update { current ->
            current.copy(
                broadcastId = broadcastId,
                streamKey = streamKey,
                hlsUrl = hlsUrl,
            )
        }
    }

    fun prepareBroadcasting() {
        val currentState = uiState.value
        if (currentState.isBroadcasting || currentState.isPreparing) {
            return
        }

        _uiState.update { current ->
            current.copy(
                error = null,
                isPreparing = true,
                srtState = SrtConnectionState.CONNECTING,
            )
        }

        viewModelScope.launch {
            broadcastStreamingUseCase.prepareBroadcastStreaming(
                streamKey = currentState.streamKey,
                mediaMtxHost = BuildConfig.MEDIA_MTX_HOST,
                mediaMtxPort = BuildConfig.MEDIA_MTX_PORT,
            ).onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        isPreparing = false,
                        isBroadcasting = false,
                        srtState = SrtConnectionState.ERROR,
                        error = throwable.message ?: "송출 준비 실패",
                    )
                }
            }
        }
    }

    fun markStreamingConnected() {
        _uiState.update { current ->
            if (!current.isPreparing && !current.isBroadcasting) {
                current
            } else {
                current.copy(srtState = SrtConnectionState.CONNECTED)
            }
        }
    }

    fun confirmBroadcastStarted(
        avatarId: String,
        onFailure: () -> Unit = {},
    ) {
        val currentState = uiState.value
        if (!currentState.isPreparing || currentState.isBroadcasting) {
            return
        }

        viewModelScope.launch {
            broadcastStreamingUseCase.confirmBroadcastStarted(
                broadcastId = currentState.broadcastId,
                avatarId = avatarId,
            ).onSuccess { broadcast ->
                heartbeatJob?.cancel()
                heartbeatJob = broadcastStreamingUseCase.startHeartbeat(
                    broadcastId = currentState.broadcastId,
                    scope = viewModelScope,
                )
                savedStateHandle[HLS_URL_KEY] = broadcast.hlsUrl.orEmpty()
                _uiState.update { state ->
                    state.copy(
                        hlsUrl = broadcast.hlsUrl.orEmpty(),
                        isPreparing = false,
                        isBroadcasting = true,
                        srtState = SrtConnectionState.CONNECTED,
                        error = null,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        isPreparing = false,
                        isBroadcasting = false,
                        srtState = SrtConnectionState.ERROR,
                        error = throwable.message ?: "방송 시작 실패",
                    )
                }
                onFailure()
            }
        }
    }

    fun cancelPreparingBroadcast(
        clearError: Boolean = true,
        message: String? = null,
    ) {
        heartbeatJob?.cancel()
        heartbeatJob = null
        _uiState.update { current ->
            current.copy(
                isPreparing = false,
                isBroadcasting = false,
                srtState = SrtConnectionState.DISCONNECTED,
                error = message ?: if (clearError) null else current.error,
            )
        }

        viewModelScope.launch {
            broadcastStreamingUseCase.stopPreparedStreaming()
        }
    }

    fun startBroadcasting(avatarId: String) {
        val currentState = uiState.value
        if (currentState.isBroadcasting) {
            return
        }

        _uiState.update { current ->
            current.copy(
                error = null,
                srtState = SrtConnectionState.CONNECTING,
            )
        }

        viewModelScope.launch {
            val result = broadcastStreamingUseCase.startBroadcast(
                broadcastId = currentState.broadcastId,
                streamKey = currentState.streamKey,
                avatarId = avatarId,
                mediaMtxHost = BuildConfig.MEDIA_MTX_HOST,
                mediaMtxPort = BuildConfig.MEDIA_MTX_PORT,
            )

            result.onSuccess {
                heartbeatJob?.cancel()
                heartbeatJob = broadcastStreamingUseCase.startHeartbeat(
                    broadcastId = currentState.broadcastId,
                    scope = viewModelScope,
                )
                _uiState.update { state ->
                    state.copy(
                        isPreparing = false,
                        isBroadcasting = true,
                        srtState = SrtConnectionState.CONNECTED,
                        error = null,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        isPreparing = false,
                        isBroadcasting = false,
                        srtState = SrtConnectionState.ERROR,
                        error = throwable.message ?: "방송 시작 실패",
                    )
                }
            }
        }
    }

    fun stopBroadcasting() {
        val currentState = uiState.value
        if (currentState.isPreparing && !currentState.isBroadcasting) {
            cancelPreparingBroadcast()
            return
        }
        if (!currentState.isBroadcasting) {
            return
        }

        heartbeatJob?.cancel()
        heartbeatJob = null
        _uiState.update { current ->
            current.copy(
                isPreparing = false,
                isBroadcasting = false,
                error = null,
                srtState = SrtConnectionState.DISCONNECTED,
            )
        }

        viewModelScope.launch {
            broadcastStreamingUseCase.stopBroadcast(currentState.broadcastId)
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            error = throwable.message ?: "방송 종료 실패",
                        )
                    }
                }
        }
    }

    override fun onCleared() {
        heartbeatJob?.cancel()
        super.onCleared()
    }
}
