package com.kmu_focus.focusandroid.core.streaming.domain.repository

import com.kmu_focus.focusandroid.core.media.data.recorder.RealTimeRecorder
import com.kmu_focus.focusandroid.core.streaming.domain.entity.SrtConnectionConfig
import com.kmu_focus.focusandroid.core.streaming.domain.entity.SrtConnectionState
import kotlinx.coroutines.flow.StateFlow

interface SrtStreamRepository {
    val connectionState: StateFlow<SrtConnectionState>

    fun createMuxerFactory(config: SrtConnectionConfig): RealTimeRecorder.VideoMuxerFactory

    suspend fun disconnect()
}
