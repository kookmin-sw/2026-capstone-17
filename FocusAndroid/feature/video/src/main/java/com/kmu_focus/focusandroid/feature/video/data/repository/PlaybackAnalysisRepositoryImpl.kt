package com.kmu_focus.focusandroid.feature.video.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import com.kmu_focus.focusandroid.core.ai.data.recognition.FaceAlignment
import com.kmu_focus.focusandroid.core.ai.data.recognition.ArcFaceEmbeddingExtractor
import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.OwnerOtherClassifier
import com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.core.ai.domain.entity.FaceLandmarks5
import com.kmu_focus.focusandroid.core.ai.domain.entity.Point2f
import com.kmu_focus.focusandroid.feature.video.data.decoder.VideoFrameDecoder
import com.kmu_focus.focusandroid.feature.video.data.pool.BitmapPool
import com.kmu_focus.focusandroid.feature.video.data.processor.FrameProcessor
import com.kmu_focus.focusandroid.feature.video.domain.entity.ProcessedFrame
import com.kmu_focus.focusandroid.feature.video.domain.repository.PlaybackAnalysisRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject

class PlaybackAnalysisRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val frameProcessor: FrameProcessor,
    private val videoFrameDecoder: VideoFrameDecoder,
    private val embeddingExtractor: ArcFaceEmbeddingExtractor,
    private val ownerClassifier: OwnerOtherClassifier,
    private val bitmapPool: BitmapPool,
) : PlaybackAnalysisRepository {

    override fun processFrame(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        timestampMs: Long,
        frameIndex: Int?,
    ): ProcessedFrame = frameProcessor.process(buffer, width, height, timestampMs, frameIndex)

    override suspend fun extractLabelsAtPosition(
        uri: String,
        positionMs: Long,
        glResult: ProcessedFrame,
    ): Map<Int, Boolean?> = withContext(Dispatchers.IO) {
        val bitmap = videoFrameDecoder.decodeFrameAt(uri, positionMs) ?: return@withContext emptyMap()
        try {
            extractLabelsFromOriginalFrame(bitmap, glResult)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    override suspend fun extractEmbeddingForTrack(
        uri: String,
        positionMs: Long,
        glResult: ProcessedFrame,
        trackId: Int,
    ): FloatArray? = withContext(Dispatchers.IO) {
        val bitmap = videoFrameDecoder.decodeFrameAt(uri, positionMs) ?: return@withContext null
        try {
            extractEmbeddingForTrackFromOriginalFrame(bitmap, glResult, trackId)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    override suspend fun saveFaceSnapshotForTrack(
        uri: String,
        positionMs: Long,
        glResult: ProcessedFrame,
        trackId: Int,
    ): String? = withContext(Dispatchers.IO) {
        val bitmap = videoFrameDecoder.decodeFrameAt(uri, positionMs) ?: return@withContext null
        try {
            saveTrackFaceSnapshotToTempFile(bitmap, glResult, trackId)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    override fun deleteTemporaryFaceSnapshot(snapshotUri: String): Boolean {
        val parsed = runCatching { Uri.parse(snapshotUri) }.getOrNull() ?: return false
        if (parsed.scheme != "file") return false
        val path = parsed.path ?: return false
        return runCatching { File(path).delete() }.getOrDefault(false)
    }

    override fun getVideoDimensions(uri: String): Pair<Int, Int>? =
        videoFrameDecoder.getVideoDimensions(uri)

    /**
     * BitmapPool로 crop 재사용하여 GC 스파이크 방지.
     */
    private fun extractLabelsFromOriginalFrame(
        originalBitmap: Bitmap,
        glResult: ProcessedFrame,
    ): Map<Int, Boolean?> {
        val out = mutableMapOf<Int, Boolean?>()
        val mapping = createFrameMapping(originalBitmap, glResult)
        for (i in glResult.faces.indices) {
            val emb = extractEmbeddingForFaceIndex(originalBitmap, glResult, i, mapping) ?: continue
            val trackId = glResult.trackingIds.getOrNull(i) ?: i
            val (isOwner, _) = ownerClassifier.decideLabel(listOf(emb))
            out[trackId] = isOwner
        }
        return out
    }

    private fun extractEmbeddingForTrackFromOriginalFrame(
        originalBitmap: Bitmap,
        glResult: ProcessedFrame,
        trackId: Int,
    ): FloatArray? {
        val faceIndex = resolveFaceIndex(glResult, trackId)
        if (faceIndex < 0) return null
        val mapping = createFrameMapping(originalBitmap, glResult)
        return extractEmbeddingForFaceIndex(originalBitmap, glResult, faceIndex, mapping)
    }

    private fun resolveFaceIndex(
        glResult: ProcessedFrame,
        trackId: Int,
    ): Int {
        val trackedIndex = glResult.trackingIds.indexOf(trackId)
        if (trackedIndex >= 0) return trackedIndex
        return trackId.takeIf { it in glResult.faces.indices } ?: -1
    }

    private fun extractEmbeddingForFaceIndex(
        originalBitmap: Bitmap,
        glResult: ProcessedFrame,
        faceIndex: Int,
        mapping: FrameMapping,
    ): FloatArray? {
        val face = glResult.faces.getOrNull(faceIndex) ?: return null
        val lm = face.landmarks
        val decodedRect = toDecodedRect(face, mapping) ?: return null
        val decodedLeft = decodedRect.left
        val decodedTop = decodedRect.top
        val decodedW = decodedRect.width()
        val decodedH = decodedRect.height()

        val crop = bitmapPool.acquire(decodedW, decodedH)
        var cropReturnedToPool = false
        try {
            val canvas = Canvas(crop)
            canvas.drawBitmap(
                originalBitmap,
                Rect(decodedLeft, decodedTop, decodedLeft + decodedW, decodedTop + decodedH),
                Rect(0, 0, decodedW, decodedH),
                null,
            )
            val aligned = if (lm != null) {
                val cropRect = Rect(0, 0, crop.width, crop.height)
                val landmarksInCrop = FaceLandmarks5(
                    rightEye = Point2f((lm.rightEye.x - face.x) / mapping.scale, (lm.rightEye.y - face.y) / mapping.scale),
                    leftEye = Point2f((lm.leftEye.x - face.x) / mapping.scale, (lm.leftEye.y - face.y) / mapping.scale),
                    nose = Point2f((lm.nose.x - face.x) / mapping.scale, (lm.nose.y - face.y) / mapping.scale),
                    rightMouth = Point2f((lm.rightMouth.x - face.x) / mapping.scale, (lm.rightMouth.y - face.y) / mapping.scale),
                    leftMouth = Point2f((lm.leftMouth.x - face.x) / mapping.scale, (lm.leftMouth.y - face.y) / mapping.scale),
                )
                FaceAlignment.alignFaceForRecognition(crop, landmarksInCrop, cropRect)
            } else {
                crop
            }
            if (aligned !== crop) {
                bitmapPool.release(crop)
                cropReturnedToPool = true
            }
            try {
                return embeddingExtractor.extractEmbedding(aligned)
            } finally {
                if (aligned !== crop && !aligned.isRecycled) aligned.recycle()
            }
        } finally {
            if (!cropReturnedToPool && !crop.isRecycled) bitmapPool.release(crop)
        }
    }

    private fun saveTrackFaceSnapshotToTempFile(
        originalBitmap: Bitmap,
        glResult: ProcessedFrame,
        trackId: Int,
    ): String? {
        val faceIndex = resolveFaceIndex(glResult, trackId)
        if (faceIndex < 0) return null
        val face = glResult.faces.getOrNull(faceIndex) ?: return null
        val mapping = createFrameMapping(originalBitmap, glResult)
        val rect = toDecodedRect(face, mapping) ?: return null

        val faceBitmap = Bitmap.createBitmap(
            originalBitmap,
            rect.left,
            rect.top,
            rect.width(),
            rect.height(),
        )
        return try {
            saveBitmapToTempFile(faceBitmap)
        } finally {
            if (!faceBitmap.isRecycled) faceBitmap.recycle()
        }
    }

    private fun toDecodedRect(
        face: DetectedFace,
        mapping: FrameMapping,
    ): Rect? {
        val decodedLeft = ((face.x - mapping.contentLeft) / mapping.scale).toInt()
            .coerceIn(0, mapping.originalWidth - 1)
        val decodedTop = ((face.y - mapping.contentTop) / mapping.scale).toInt()
            .coerceIn(0, mapping.originalHeight - 1)
        val decodedW = (face.width / mapping.scale).toInt()
            .coerceIn(1, mapping.originalWidth - decodedLeft)
        val decodedH = (face.height / mapping.scale).toInt()
            .coerceIn(1, mapping.originalHeight - decodedTop)
        if (decodedW <= 0 || decodedH <= 0) return null
        return Rect(
            decodedLeft,
            decodedTop,
            decodedLeft + decodedW,
            decodedTop + decodedH,
        )
    }

    private fun saveBitmapToTempFile(bitmap: Bitmap): String? {
        val snapshotsDir = File(context.cacheDir, TEMP_SNAPSHOT_DIR).apply { mkdirs() }
        val file = File(snapshotsDir, "owner_face_${UUID.randomUUID()}.jpg")
        val written = runCatching {
            file.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, SNAPSHOT_JPEG_QUALITY, output)
            }
        }.getOrDefault(false)
        if (!written) {
            file.delete()
            return null
        }
        return Uri.fromFile(file).toString()
    }

    private fun createFrameMapping(
        originalBitmap: Bitmap,
        glResult: ProcessedFrame,
    ): FrameMapping {
        val gw = glResult.frameWidth.coerceAtLeast(1)
        val gh = glResult.frameHeight.coerceAtLeast(1)
        val ow = originalBitmap.width
        val oh = originalBitmap.height
        val scale = minOf(gw / ow.toFloat(), gh / oh.toFloat())
        val contentW = ow * scale
        val contentH = oh * scale
        val contentLeft = (gw - contentW) / 2f
        val contentTop = (gh - contentH) / 2f
        return FrameMapping(
            scale = scale,
            contentLeft = contentLeft,
            contentTop = contentTop,
            originalWidth = ow,
            originalHeight = oh,
        )
    }

    private data class FrameMapping(
        val scale: Float,
        val contentLeft: Float,
        val contentTop: Float,
        val originalWidth: Int,
        val originalHeight: Int,
    )

    private companion object {
        private const val SNAPSHOT_JPEG_QUALITY = 95
        private const val TEMP_SNAPSHOT_DIR = "owner_snapshots"
    }
}
