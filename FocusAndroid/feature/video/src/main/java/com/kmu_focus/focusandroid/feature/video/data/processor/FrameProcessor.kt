package com.kmu_focus.focusandroid.feature.video.data.processor

import android.graphics.Bitmap
import com.kmu_focus.focusandroid.feature.detection.domain.config.DetectionConfig
import com.kmu_focus.focusandroid.feature.detection.domain.detector.FaceDetector
import com.kmu_focus.focusandroid.feature.video.domain.entity.ProcessedFrame
import java.nio.ByteBuffer
import javax.inject.Inject

class FrameProcessor @Inject constructor(
    private val faceDetector: FaceDetector,
    private val config: DetectionConfig
) {
    fun process(bitmap: Bitmap, timestampMs: Long): ProcessedFrame {
        val faces = faceDetector.detectFaces(bitmap)
            .filter { it.confidence >= config.confidenceThreshold }
        return ProcessedFrame(
            faces = faces,
            frameWidth = bitmap.width,
            frameHeight = bitmap.height,
            timestampMs = timestampMs
        )
    }

    // GL PBO에서 읽은 RGBA ByteBuffer를 Bitmap으로 변환 후 검출
    fun process(rgbaBuffer: ByteBuffer, width: Int, height: Int, timestampMs: Long): ProcessedFrame {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        rgbaBuffer.rewind()
        bitmap.copyPixelsFromBuffer(rgbaBuffer)

        return try {
            process(bitmap, timestampMs)
        } finally {
            bitmap.recycle()
        }
    }
}
