package com.kmu_focus.focusandroid.feature.video.data.processor

import android.graphics.Bitmap
import android.graphics.Rect
import com.kmu_focus.focusandroid.feature.ai.domain.config.DetectionConfig
import com.kmu_focus.focusandroid.feature.ai.domain.detector.FaceDetector
import com.kmu_focus.focusandroid.feature.ai.domain.detector.Facial3DMMExtractor
import com.kmu_focus.focusandroid.feature.ai.domain.detector.tracking.FaceTracker
import com.kmu_focus.focusandroid.feature.ai.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.feature.video.domain.entity.FaceExport
import com.kmu_focus.focusandroid.feature.video.domain.entity.FrameExport
import com.kmu_focus.focusandroid.feature.video.domain.entity.ProcessedFrame
import java.nio.ByteBuffer
import javax.inject.Inject

class FrameProcessor @Inject constructor(
    private val faceDetector: FaceDetector,
    private val config: DetectionConfig,
    private val facial3DMMExtractor: Facial3DMMExtractor,
    private val faceTracker: FaceTracker
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

        val frameExport = if (frameIndex != null && faces.isNotEmpty()) {
            val facesExport = faces.mapIndexed { idx, face ->
                val raw3dmm = raw3dmmList.getOrNull(idx)
                FaceExport(
                    trackingId = trackingIds.getOrElse(idx) { idx },
                    bbox = intArrayOf(face.x, face.y, face.width, face.height),
                    idCoeffs = raw3dmm?.idCoeffs,
                    expCoeffs = raw3dmm?.expCoeffs,
                    pose = raw3dmm?.pose
                )
            }
            FrameExport(
                frameNumber = frameIndex,
                timestamp = timestampMs / 1000.0,
                faces = facesExport
            )
        } else null

        return ProcessedFrame(
            faces = faces,
            frameWidth = bitmap.width,
            frameHeight = bitmap.height,
            timestampMs = timestampMs,
            frameExport = frameExport,
            trackingIds = trackingIds
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
