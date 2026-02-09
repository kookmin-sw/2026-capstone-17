package com.kmu_focus.focusandroid.feature.video.data.processor

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.kmu_focus.focusandroid.core.ai.data.recognition.FaceAlignment
import com.kmu_focus.focusandroid.core.ai.data.recognition.ArcFaceEmbeddingExtractor
import com.kmu_focus.focusandroid.core.ai.domain.config.DetectionConfig
import com.kmu_focus.focusandroid.core.ai.domain.detector.FaceDetector
import com.kmu_focus.focusandroid.core.ai.domain.detector.Facial3DMMExtractor
import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.TrackLabelState
import com.kmu_focus.focusandroid.core.ai.domain.detector.tracking.FaceTracker
import com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.feature.video.domain.entity.FaceExport
import com.kmu_focus.focusandroid.feature.video.domain.entity.FrameExport
import com.kmu_focus.focusandroid.feature.video.domain.entity.ProcessedFrame
import java.nio.ByteBuffer
import javax.inject.Inject

class FrameProcessor @Inject constructor(
    private val faceDetector: FaceDetector,
    private val config: DetectionConfig,
    private val facial3DMMExtractor: Facial3DMMExtractor,
    private val faceTracker: FaceTracker,
    private val trackLabelState: TrackLabelState,
    private val embeddingExtractor: ArcFaceEmbeddingExtractor
) {
    fun process(bitmap: Bitmap, timestampMs: Long, frameIndex: Int? = null): ProcessedFrame {
        val faces = faceDetector.detectFaces(bitmap)
            .filter { it.confidence >= config.confidenceThreshold }

        val raw3dmmList = if (faces.isNotEmpty()) {
            faces.map { face ->
                val rect = Rect(face.x, face.y, face.x + face.width, face.y + face.height)
                facial3DMMExtractor.extract3DMM(bitmap, rect)?.coeffs
            }
        } else emptyList()

        val trackingIds: List<Int> = if (frameIndex != null && faces.isNotEmpty()) {
            val detections = faces.map { intArrayOf(it.x, it.y, it.width, it.height) }
            faceTracker.update(detections, raw3dmmList.map { it?.idCoeffs })
        } else {
            faces.indices.toList()
        }

        fun hasValidLandmarks(idx: Int): Boolean = raw3dmmList.getOrNull(idx) != null

        if (faces.isNotEmpty()) {
            trackLabelState.beginFrame(trackingIds.toSet())
            for (idx in faces.indices) {
                if (!hasValidLandmarks(idx)) continue
                val face = faces[idx]
                val trackId = trackingIds.getOrElse(idx) { idx }
                trackLabelState.recordFrameSeen(trackId)
                val isFrontal = face.landmarks?.isFrontal(0.4f) ?: false
                if (!trackLabelState.needsEmbeddingThisFrame(trackId, isFrontal)) continue
                val rect = Rect(face.x, face.y, face.x + face.width, face.y + face.height)
                if (rect.width() < 16 || rect.height() < 16) continue
                var crop = Bitmap.createBitmap(
                    bitmap,
                    rect.left.coerceIn(0, bitmap.width - 1),
                    rect.top.coerceIn(0, bitmap.height - 1),
                    rect.width().coerceIn(1, bitmap.width - rect.left),
                    rect.height().coerceIn(1, bitmap.height - rect.top)
                )
                face.landmarks?.let { lm ->
                    val aligned = FaceAlignment.alignFaceForRecognition(crop, lm, rect)
                    if (aligned != crop) {
                        crop.recycle()
                        crop = aligned
                    }
                }
                if (trackId in setOf(15, 31, 38, 39, 43)) {
                    Log.i("FrameProcessor", "ID:$trackId 프리뷰 프레임 ${bitmap.width}x${bitmap.height} | ArcFace 입력 전 crop ${crop.width}x${crop.height}")
                }
                embeddingExtractor.extractEmbedding(crop)?.let { emb ->
                    when (trackLabelState.getLabel(trackId)) {
                        null -> trackLabelState.addEmbedding(trackId, emb)
                        false -> trackLabelState.recheckFrontal(trackId, emb)
                        true -> { }
                    }
                }
                crop.recycle()
            }
        }

        val frameExport = if (frameIndex != null && faces.isNotEmpty()) {
            val facesExport = faces.mapIndexed { idx, face ->
                val raw3dmm = raw3dmmList.getOrNull(idx)
                val trackId = trackingIds.getOrElse(idx) { idx }
                val isOwner = trackLabelState.getLabel(trackId)
                FaceExport(
                    trackingId = trackId,
                    bbox = intArrayOf(face.x, face.y, face.width, face.height),
                    idCoeffs = raw3dmm?.idCoeffs,
                    expCoeffs = raw3dmm?.expCoeffs,
                    pose = raw3dmm?.pose,
                    isOwner = isOwner
                )
            }
            FrameExport(
                frameNumber = frameIndex,
                timestamp = timestampMs / 1000.0,
                faces = facesExport
            )
        } else null

        val faceLabels = faces.indices.map { trackLabelState.getLabel(trackingIds.getOrElse(it) { it }) }
        return ProcessedFrame(
            faces = faces,
            frameWidth = bitmap.width,
            frameHeight = bitmap.height,
            timestampMs = timestampMs,
            frameExport = frameExport,
            trackingIds = trackingIds,
            faceLabels = faceLabels
        )
    }

    fun process(rgbaBuffer: ByteBuffer, width: Int, height: Int, timestampMs: Long, frameIndex: Int? = null): ProcessedFrame {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        rgbaBuffer.rewind()
        bitmap.copyPixelsFromBuffer(rgbaBuffer)
        return try {
            process(bitmap, timestampMs, frameIndex)
        } finally {
            bitmap.recycle()
        }
    }
}
