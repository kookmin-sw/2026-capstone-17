package com.kmu_focus.focusandroid.feature.broadcast.domain.usecase

import com.kmu_focus.focusandroid.core.media.data.recorder.RealTimeRecorder
import com.kmu_focus.focusandroid.core.streaming.domain.entity.SrtConnectionConfig
import com.kmu_focus.focusandroid.core.streaming.domain.repository.SrtStreamRepository
import com.kmu_focus.focusandroid.feature.broadcast.domain.repository.BroadcastRepository
import javax.inject.Inject
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BroadcastStreamingUseCase @Inject constructor(
    private val srtStreamRepository: SrtStreamRepository,
    private val broadcastRepository: BroadcastRepository,
) {
    val connectionState = srtStreamRepository.connectionState

    var currentMuxerFactory: RealTimeRecorder.VideoMuxerFactory? = null
        private set

    suspend fun prepareBroadcastStreaming(
        streamKey: String,
        mediaMtxHost: String,
        mediaMtxPort: Int,
    ): Result<Unit> {
        val config = SrtConnectionConfig(
            host = mediaMtxHost,
            port = mediaMtxPort,
            streamKey = streamKey,
        )

        return try {
            currentMuxerFactory = srtStreamRepository.createMuxerFactory(config)
            Result.success(Unit)
        } catch (throwable: Throwable) {
            currentMuxerFactory = null
            Result.failure(throwable)
        }
    }

    suspend fun confirmBroadcastStarted(
        broadcastId: String,
        avatarId: String,
    ): Result<com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast> {
        if (currentMuxerFactory == null) {
            return Result.failure(IllegalStateException("SRT 송출 준비가 완료되지 않았습니다"))
        }

        return try {
            broadcastRepository.startBroadcast(
                broadcastId = broadcastId,
                avatarId = avatarId,
            )
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }

    suspend fun abortPreparedBroadcast() {
        try {
            srtStreamRepository.disconnect()
        } finally {
            currentMuxerFactory = null
        }
    }

    suspend fun startBroadcast(
        broadcastId: String,
        streamKey: String,
        avatarId: String,
        mediaMtxHost: String,
        mediaMtxPort: Int,
    ): Result<Unit> {
        val prepareResult = prepareBroadcastStreaming(
            streamKey = streamKey,
            mediaMtxHost = mediaMtxHost,
            mediaMtxPort = mediaMtxPort,
        )
        if (prepareResult.isFailure) {
            return Result.failure(prepareResult.exceptionOrNull() ?: IllegalStateException("방송 준비 실패"))
        }

        return confirmBroadcastStarted(
            broadcastId = broadcastId,
            avatarId = avatarId,
        ).fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { throwable ->
                abortPreparedBroadcast()
                Result.failure(throwable)
            },
        )
    }

    suspend fun stopPreparedStreaming() {
        abortPreparedBroadcast()
    }

    suspend fun stopBroadcast(broadcastId: String): Result<Unit> {
        return try {
            srtStreamRepository.disconnect()
            currentMuxerFactory = null
            broadcastRepository.stopBroadcast(broadcastId).getOrThrow()
            Result.success(Unit)
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }

    fun startHeartbeat(
        broadcastId: String,
        scope: CoroutineScope,
    ): Job {
        return scope.launch {
            var consecutiveFailures = 0
            while (isActive) {
                delay(10_000)
                val result = runCatching {
                    broadcastRepository.sendStreamerHeartbeat(broadcastId)
                }.getOrElse { Result.failure(it) }

                if (result.isSuccess) {
                    consecutiveFailures = 0
                } else {
                    consecutiveFailures += 1
                    if (consecutiveFailures >= 3) {
                        cancel()
                    }
                }
            }
        }
    }
}
