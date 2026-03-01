package com.kmu_focus.focusandroid.feature.camera.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.kmu_focus.focusandroid.core.ai.data.recognition.ArcFaceEmbeddingExtractor
import com.kmu_focus.focusandroid.core.ai.data.recognition.FaceAlignment
import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.OwnerAdder
import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.TrackLabelState
import com.kmu_focus.focusandroid.core.metadata.domain.mapper.MetadataMapper
import com.kmu_focus.focusandroid.core.metadata.domain.repository.MetadataRepository
import com.kmu_focus.focusandroid.feature.camera.domain.entity.OwnerRegistrationResult
import com.kmu_focus.focusandroid.feature.camera.domain.repository.CameraAnalysisRepository
import com.kmu_focus.focusandroid.core.media.data.processor.FrameProcessor
import com.kmu_focus.focusandroid.core.media.di.IoDispatcher
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class CameraAnalysisRepositoryImpl @Inject constructor(
    private val frameProcessor: FrameProcessor,
    private val metadataRepositoryProvider: Provider<MetadataRepository>,
    private val ownerAdder: OwnerAdder,
    private val trackLabelState: TrackLabelState,
    private val embeddingExtractor: ArcFaceEmbeddingExtractor,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CameraAnalysisRepository {

    companion object {
        private const val TAG = "CameraAnalysisRepo"
        private const val THUMBNAIL_QUALITY = 95
        private const val SNAPSHOT_DIR = "owner_snapshots"
    }
    private val metadataScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val metadataJobs = mutableSetOf<Job>()
    private val metadataJobsLock = Any()
    private val metadataStateLock = Any()
    private var metadataRepository: MetadataRepository? = null
    private var metadataSessionId: String? = null
    private var metadataEnabled = false
    private var metadataFrameIndex = 0

    override fun registerOwnerFromFrame(
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        trackId: Int,
        processedFrame: ProcessedFrame,
    ): OwnerRegistrationResult {
        val faceIndex = processedFrame.trackingIds.indexOf(trackId)
        if (faceIndex < 0) {
            Log.w(TAG, "registerOwner: trackId=$trackId not found")
            return OwnerRegistrationResult(success = false)
        }

        val face = processedFrame.faces[faceIndex]
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        rgbaBuffer.rewind()
        bitmap.copyPixelsFromBuffer(rgbaBuffer)

        val rect = Rect(
            face.x.coerceIn(0, bitmap.width - 1),
            face.y.coerceIn(0, bitmap.height - 1),
            (face.x + face.width).coerceIn(1, bitmap.width),
            (face.y + face.height).coerceIn(1, bitmap.height),
        )
        if (rect.width() < 16 || rect.height() < 16) {
            bitmap.recycle()
            Log.w(TAG, "registerOwner: face too small")
            return OwnerRegistrationResult(success = false)
        }

        var crop = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
        face.landmarks?.let { lm ->
            val aligned = FaceAlignment.alignFaceForRecognition(crop, lm, rect)
            if (aligned != crop) {
                crop.recycle()
                crop = aligned
            }
        }

        val embedding = embeddingExtractor.extractEmbedding(crop)
        if (embedding == null) {
            crop.recycle()
            bitmap.recycle()
            Log.w(TAG, "registerOwner: embedding extraction failed")
            return OwnerRegistrationResult(success = false)
        }

        val success = ownerAdder.addOwnerFromEmbedding(embedding)
        val thumbnailPath = if (success) saveFaceThumbnail(crop) else null

        if (success) {
            trackLabelState.removeTrack(trackId)
            Log.i(TAG, "registerOwner: trackId=$trackId registered, thumbnail=$thumbnailPath")
        }

        crop.recycle()
        bitmap.recycle()
        return OwnerRegistrationResult(success = success, thumbnailPath = thumbnailPath)
    }

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

    private fun saveFaceThumbnail(faceBitmap: Bitmap): String? = runCatching {
        val dir = File(context.cacheDir, SNAPSHOT_DIR).apply { mkdirs() }
        val file = File(dir, "owner_face_${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { out ->
            faceBitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, out)
        }
        file.absolutePath
    }.getOrNull()
}
