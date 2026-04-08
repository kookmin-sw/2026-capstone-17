package com.kmu_focus.focusandroid.core.ai.data.recognition

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import com.kmu_focus.focusandroid.core.ai.domain.entity.FaceLandmarks5
import kotlin.math.abs

/**
 * 랜드마크 기반 얼굴 정렬 (ArcFace 입력 품질 향상).
 * getFaceAngle()은 라디안 → 도 변환 후 회전. 테스트 프로젝트와 동일.
 */
object FaceAlignment {

    private const val MIN_ANGLE_DEG = 1.5f

    fun alignFaceForRecognition(
        crop: Bitmap,
        landmarks: FaceLandmarks5,
        faceRect: Rect
    ): Bitmap {
        val angleRad = landmarks.getFaceAngle()
        val angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
        if (abs(angleDeg) < MIN_ANGLE_DEG) return crop

        val centerX = crop.width / 2f
        val centerY = crop.height / 2f
        val matrix = Matrix().apply { postRotate(-angleDeg, centerX, centerY) }
        val out = Bitmap.createBitmap(crop.width, crop.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(crop, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
