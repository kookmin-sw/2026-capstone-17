package com.kmu_focus.focusandroid.core.media.presentation.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace

@Composable
fun FaceDetectionOverlay(
    faces: List<DetectedFace>,
    frameWidth: Int,
    frameHeight: Int,
    faceLabels: List<Boolean?> = emptyList(),
    trackingIds: List<Int> = emptyList(),
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (frameWidth <= 0 || frameHeight <= 0) return@Canvas

        val scaleX = size.width / frameWidth
        val scaleY = size.height / frameHeight

        faces.forEachIndexed { index, face ->
            val left = face.x * scaleX
            val top = face.y * scaleY
            val w = face.width * scaleX
            val h = face.height * scaleY

            val trackId = trackingIds.getOrNull(index) ?: index
            val (boxColor, roleLabel) = when (faceLabels.getOrNull(index)) {
                true -> Color.Green to "OWNER"
                false -> Color.Red to "OTHER"
                null -> Color(0xFFFFC107) to "대기"
            }
            val label = "ID:$trackId $roleLabel"

            drawRect(
                color = boxColor,
                topLeft = Offset(left, top),
                size = Size(w, h),
                style = Stroke(width = 3f)
            )

            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 32f
                    isAntiAlias = true
                    setShadowLayer(2f, 0f, 0f, android.graphics.Color.BLACK)
                }
                canvas.nativeCanvas.drawText(
                    label,
                    left,
                    (top - 8f).coerceAtLeast(paint.textSize),
                    paint
                )
            }
        }
    }
}
