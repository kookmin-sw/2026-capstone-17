package com.kmu_focus.focusandroid.feature.video.data.repository

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import com.kmu_focus.focusandroid.core.ai.data.recognition.FaceAlignment
import com.kmu_focus.focusandroid.core.ai.data.recognition.ArcFaceEmbeddingExtractor
import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.OwnerOtherClassifier
import com.kmu_focus.focusandroid.core.ai.domain.entity.FaceLandmarks5
import com.kmu_focus.focusandroid.core.ai.domain.entity.Point2f
import com.kmu_focus.focusandroid.feature.video.data.decoder.VideoFrameDecoder
import com.kmu_focus.focusandroid.feature.video.data.pool.BitmapPool
import com.kmu_focus.focusandroid.feature.video.data.processor.FrameProcessor
import com.kmu_focus.focusandroid.feature.video.domain.entity.ProcessedFrame
import com.kmu_focus.focusandroid.feature.video.domain.repository.PlaybackAnalysisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import javax.inject.Inject

class PlaybackAnalysisRepositoryImpl @Inject constructor(
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
        val gw = glResult.frameWidth.coerceAtLeast(1)
        val gh = glResult.frameHeight.coerceAtLeast(1)
        val ow = originalBitmap.width
        val oh = originalBitmap.height
        val scale = minOf(gw / ow.toFloat(), gh / oh.toFloat())
        val contentW = ow * scale
        val contentH = oh * scale
        val contentLeft = (gw - contentW) / 2f
        val contentTop = (gh - contentH) / 2f

        for (i in glResult.faces.indices) {
            val face = glResult.faces[i]
            val trackId = glResult.trackingIds.getOrNull(i) ?: i
            val lm = face.landmarks ?: continue
            val decodedLeft = ((face.x - contentLeft) / scale).toInt().coerceIn(0, ow - 1)
            val decodedTop = ((face.y - contentTop) / scale).toInt().coerceIn(0, oh - 1)
            val decodedW = (face.width / scale).toInt().coerceIn(1, ow - decodedLeft)
            val decodedH = (face.height / scale).toInt().coerceIn(1, oh - decodedTop)

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
                val cropRect = Rect(0, 0, crop.width, crop.height)
                val landmarksInCrop = FaceLandmarks5(
                    rightEye = Point2f((lm.rightEye.x - face.x) / scale, (lm.rightEye.y - face.y) / scale),
                    leftEye = Point2f((lm.leftEye.x - face.x) / scale, (lm.leftEye.y - face.y) / scale),
                    nose = Point2f((lm.nose.x - face.x) / scale, (lm.nose.y - face.y) / scale),
                    rightMouth = Point2f((lm.rightMouth.x - face.x) / scale, (lm.rightMouth.y - face.y) / scale),
                    leftMouth = Point2f((lm.leftMouth.x - face.x) / scale, (lm.leftMouth.y - face.y) / scale),
                )
                val aligned = FaceAlignment.alignFaceForRecognition(crop, landmarksInCrop, cropRect)
                if (aligned != crop) {
                    bitmapPool.release(crop)
                    cropReturnedToPool = true
                }
                try {
                    val emb = embeddingExtractor.extractEmbedding(aligned) ?: continue
                    val (isOwner, _) = ownerClassifier.decideLabel(listOf(emb))
                    out[trackId] = isOwner
                } finally {
                    if (aligned != crop && !aligned.isRecycled) aligned.recycle()
                }
            } finally {
                if (!cropReturnedToPool && !crop.isRecycled) bitmapPool.release(crop)
            }
        }
        return out
    }
}
