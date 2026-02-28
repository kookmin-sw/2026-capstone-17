package com.kmu_focus.focusandroid.feature.camera.data.repository

import com.kmu_focus.focusandroid.core.metadata.domain.mapper.MetadataMapper
import com.kmu_focus.focusandroid.core.metadata.domain.repository.MetadataRepository
import com.kmu_focus.focusandroid.feature.camera.domain.repository.CameraAnalysisRepository
import com.kmu_focus.focusandroid.core.media.data.processor.FrameProcessor
import com.kmu_focus.focusandroid.core.media.di.IoDispatcher
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.UUID

class CameraAnalysisRepositoryImpl @Inject constructor(
    private val frameProcessor: FrameProcessor,
    private val metadataRepositoryProvider: Provider<MetadataRepository>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CameraAnalysisRepository {
    private val metadataScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val metadataJobs = mutableSetOf<Job>()
    private val metadataJobsLock = Any()
    private val metadataStateLock = Any()
    private var metadataRepository: MetadataRepository? = null
    private var metadataSessionId: String? = null
    private var metadataEnabled = false
    private var metadataFrameIndex = 0

    override fun processFrame(
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        timestampMs: Long,
    ): ProcessedFrame {
        val frameIndex = synchronized(metadataStateLock) {
            if (metadataEnabled) {
                metadataFrameIndex++
                metadataFrameIndex - 1
            } else {
                null
            }
        }
        val processed = frameProcessor.process(
            rgbaBuffer = rgbaBuffer,
            width = width,
            height = height,
            timestampMs = timestampMs,
            frameIndex = frameIndex,
        )
        if (frameIndex != null) {
            enqueueMetadataFrame(processed)
        }
        return processed
    }

    override fun clearProcessingThreadCache() {
        frameProcessor.clearThreadLocalCache()
    }

    override fun startMetadataSession() {
        synchronized(metadataStateLock) {
            metadataEnabled = true
            metadataFrameIndex = 0
            metadataSessionId = null
        }
    }

    override suspend fun closeMetadataSession() {
        synchronized(metadataStateLock) {
            metadataEnabled = false
        }

        val jobs = synchronized(metadataJobsLock) { metadataJobs.toList() }
        jobs.joinAll()

        val repo = synchronized(metadataStateLock) {
            val current = metadataRepository
            metadataRepository = null
            metadataSessionId = null
            metadataFrameIndex = 0
            current
        }
        repo?.close()
    }

    private fun enqueueMetadataFrame(frame: ProcessedFrame) {
        val frameExport = frame.frameExport ?: return
        val sessionId = synchronized(metadataStateLock) {
            metadataSessionId ?: UUID.randomUUID().toString().also { metadataSessionId = it }
        }
        val metadata = MetadataMapper.mapFrame(
            sessionId = sessionId,
            timestampSeconds = frameExport.timestamp,
            faces = frameExport.faces.map { face ->
                MetadataMapper.FaceExportPayload(
                    trackingId = face.trackingId,
                    bbox = face.bbox,
                    idCoeffs = face.idCoeffs,
                    expCoeffs = face.expCoeffs,
                    pose = face.pose,
                    extraCoeffs = face.extraCoeffs,
                    isOwner = face.isOwner,
                )
            },
        )

        launchMetadataJob {
            val repo = synchronized(metadataStateLock) {
                metadataRepository ?: metadataRepositoryProvider.get().also {
                    metadataRepository = it
                }
            }
            repo.sendFrame(metadata)
        }
    }

    private fun launchMetadataJob(block: suspend () -> Unit) {
        val job = metadataScope.launch {
            runCatching { block() }
        }
        synchronized(metadataJobsLock) {
            metadataJobs += job
        }
        job.invokeOnCompletion {
            synchronized(metadataJobsLock) {
                metadataJobs -= job
            }
        }
    }
}
