package com.kmu_focus.focusandroid.feature.video.data.processor

import android.graphics.Bitmap
import android.graphics.Rect
import com.kmu_focus.focusandroid.feature.detection.domain.config.DetectionConfig
import com.kmu_focus.focusandroid.feature.detection.domain.detector.FaceDetector
import com.kmu_focus.focusandroid.feature.detection.domain.detector.FacialLandmarkDetector
import com.kmu_focus.focusandroid.feature.detection.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.feature.video.domain.entity.FaceExport
import com.kmu_focus.focusandroid.feature.video.domain.entity.FrameExport
import com.kmu_focus.focusandroid.feature.video.domain.entity.ProcessedFrame
import java.nio.ByteBuffer
import javax.inject.Inject

class FrameProcessor @Inject constructor(
    private val faceDetector: FaceDetector,
    private val config: DetectionConfig,
    private val landmarkDetector: FacialLandmarkDetector
) {
    fun process(bitmap: Bitmap, timestampMs: Long, frameIndex: Int? = null): ProcessedFrame {
        val faces = faceDetector.detectFaces(bitmap)
            .filter { it.confidence >= config.confidenceThreshold }

        val frameExport = if (frameIndex != null && faces.isNotEmpty()) {
            val raw3dmmList = faces.map { face ->
                val rect = Rect(face.x, face.y, face.x + face.width, face.y + face.height)
                landmarkDetector.detectLandmarks(bitmap, rect)?.coeffs
            }
            val facesExport = faces.mapIndexed { idx, face ->
                val raw3dmm = raw3dmmList.getOrNull(idx)
                FaceExport(
                    trackingId = idx,
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
            frameExport = frameExport
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
