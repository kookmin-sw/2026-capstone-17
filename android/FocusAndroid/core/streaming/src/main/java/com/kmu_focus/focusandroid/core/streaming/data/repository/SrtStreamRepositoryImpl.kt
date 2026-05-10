package com.kmu_focus.focusandroid.core.streaming.data.repository

import com.kmu_focus.focusandroid.core.media.data.recorder.RealTimeRecorder
import com.kmu_focus.focusandroid.core.streaming.data.srt.SrtVideoMuxerFactory
import com.kmu_focus.focusandroid.core.streaming.domain.entity.SrtConnectionConfig
import com.kmu_focus.focusandroid.core.streaming.domain.entity.SrtConnectionState
import com.kmu_focus.focusandroid.core.streaming.domain.repository.SrtStreamRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SrtStreamRepositoryImpl @Inject constructor() : SrtStreamRepository {
    private val _connectionState = MutableStateFlow(SrtConnectionState.DISCONNECTED)

    override val connectionState: StateFlow<SrtConnectionState> = _connectionState.asStateFlow()

    override fun createMuxerFactory(config: SrtConnectionConfig): RealTimeRecorder.VideoMuxerFactory {
        return SrtVideoMuxerFactory(config)
    }

    override suspend fun disconnect() {
        _connectionState.value = SrtConnectionState.DISCONNECTED
    }
}
