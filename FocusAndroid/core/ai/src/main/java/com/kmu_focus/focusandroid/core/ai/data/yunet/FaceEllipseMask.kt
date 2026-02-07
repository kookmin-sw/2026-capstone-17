package com.kmu_focus.focusandroid.core.ai.data.yunet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.kmu_focus.focusandroid.core.ai.domain.entity.FaceLandmarks5
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object FaceEllipseMask {

    fun createMask(
        width: Int,
        height: Int,
        landmarks: FaceLandmarks5,
        paddingRatio: Float = 1.05f
    ): Bitmap {
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(mask)
        canvas.drawColor(Color.TRANSPARENT)
        val ellipse = calculateEllipseParams(landmarks, paddingRatio)
        val paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.save()
        canvas.rotate(ellipse.angleDeg, ellipse.centerX, ellipse.centerY)
        canvas.drawOval(
            ellipse.centerX - ellipse.radiusX,
            ellipse.centerY - ellipse.radiusY,
            ellipse.centerX + ellipse.radiusX,
            ellipse.centerY + ellipse.radiusY,
            paint
        )
        canvas.restore()
        return mask
    }

    fun getEllipseBounds(landmarks: FaceLandmarks5, paddingRatio: Float = 1.05f): RectF {
        val ellipse = calculateEllipseParams(landmarks, paddingRatio)
        return calculateEllipseBounds(ellipse)
    }

    fun drawEllipseOutline(
        canvas: Canvas,
        landmarks: FaceLandmarks5,
        paint: Paint,
        paddingRatio: Float = 1.05f
    ) {
        val ellipse = calculateEllipseParams(landmarks, paddingRatio)
        canvas.save()
        canvas.rotate(ellipse.angleDeg, ellipse.centerX, ellipse.centerY)
        canvas.drawOval(
            ellipse.centerX - ellipse.radiusX,
            ellipse.centerY - ellipse.radiusY,
            ellipse.centerX + ellipse.radiusX,
            ellipse.centerY + ellipse.radiusY,
            paint
        )
        canvas.restore()
    }

    internal fun calculateEllipseParams(landmarks: FaceLandmarks5, paddingRatio: Float): EllipseParams {
        val eyeCenter = landmarks.getEyeCenter()
        val mouthCenter = landmarks.getMouthCenter()
        val eyeDistance = landmarks.getEyeDistance()
        val angleDeg = landmarks.getFaceAngle()

        val eyeMouthDist = sqrt(
            (mouthCenter.x - eyeCenter.x) * (mouthCenter.x - eyeCenter.x) +
                (mouthCenter.y - eyeCenter.y) * (mouthCenter.y - eyeCenter.y)
        )
        val centerX = eyeCenter.x + (mouthCenter.x - eyeCenter.x) * 0.3f
        val centerY = eyeCenter.y + (mouthCenter.y - eyeCenter.y) * 0.3f
        val radiusX = eyeDistance * 1.0f * paddingRatio
        val radiusY = eyeMouthDist * 1.35f * paddingRatio
        return EllipseParams(centerX, centerY, radiusX, radiusY, angleDeg)
    }

    private fun calculateEllipseBounds(ellipse: EllipseParams): RectF {
        val angleRad = Math.toRadians(ellipse.angleDeg.toDouble()).toFloat()
        val c = cos(angleRad)
        val s = sin(angleRad)
        val ux = ellipse.radiusX * c
        val uy = ellipse.radiusX * s
        val vx = ellipse.radiusY * -s
        val vy = ellipse.radiusY * c
        val halfWidth = sqrt(ux * ux + vx * vx)
        val halfHeight = sqrt(uy * uy + vy * vy)
        return RectF(
            ellipse.centerX - halfWidth,
            ellipse.centerY - halfHeight,
            ellipse.centerX + halfWidth,
            ellipse.centerY + halfHeight
        )
    }

    data class EllipseParams(
        val centerX: Float,
        val centerY: Float,
        val radiusX: Float,
        val radiusY: Float,
        val angleDeg: Float
    )
}
