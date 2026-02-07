package com.kmu_focus.focusandroid.feature.video.presentation.videoplayer

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.kmu_focus.focusandroid.feature.detection.domain.entity.DetectedFace

@Composable
fun FaceDetectionOverlay(
    faces: List<DetectedFace>,
    frameWidth: Int,
    frameHeight: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (frameWidth <= 0 || frameHeight <= 0) return@Canvas

        val scaleX = size.width / frameWidth
        val scaleY = size.height / frameHeight

        faces.forEach { face ->
            val left = face.x * scaleX
            val top = face.y * scaleY
            val w = face.width * scaleX
            val h = face.height * scaleY

            drawRect(
                color = Color.Green,
                topLeft = Offset(left, top),
                size = Size(w, h),
                style = Stroke(width = 3f)
            )

            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GREEN
                    textSize = 32f
                    isAntiAlias = true
                }
                canvas.nativeCanvas.drawText(
                    String.format("%.0f%%", face.confidence * 100),
                    left,
                    (top - 8f).coerceAtLeast(paint.textSize),
                    paint
                )
            }
        }
    }
}
