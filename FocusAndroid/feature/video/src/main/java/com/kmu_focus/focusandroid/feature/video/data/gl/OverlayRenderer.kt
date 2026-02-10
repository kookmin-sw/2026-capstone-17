package com.kmu_focus.focusandroid.feature.video.data.gl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES30
import android.opengl.GLUtils
import com.kmu_focus.focusandroid.feature.video.domain.entity.ProcessedFrame

/**
 * ProcessedFrame의 검출 결과를 투명 Bitmap에 Canvas로 그린 뒤
 * GL 텍스처에 업로드하여 FBO 위에 알파 블렌딩할 수 있게 한다.
 */
class OverlayRenderer {

    private var overlayTexId = 0
    private var overlayBitmap: Bitmap? = null
    private var overlayCanvas: Canvas? = null

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
        setShadowLayer(2f, 0f, 0f, Color.BLACK)
    }

    fun init(width: Int, height: Int) {
        overlayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        overlayCanvas = Canvas(overlayBitmap!!)

        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        overlayTexId = texIds[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, overlayTexId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    /**
     * ProcessedFrame의 박스/라벨을 그려서 GL 텍스처에 업로드한다.
     * @return 오버레이 텍스처 ID (draw2DBlend에서 사용)
     */
    fun drawOverlay(frame: ProcessedFrame): Int {
        val bitmap = overlayBitmap ?: return overlayTexId
        val canvas = overlayCanvas ?: return overlayTexId

        bitmap.eraseColor(Color.TRANSPARENT)

        frame.faces.forEachIndexed { index, face ->
            val trackId = frame.trackingIds.getOrNull(index) ?: index
            val label = frame.faceLabels.getOrNull(index)

            val (color, roleLabel) = when (label) {
                true -> Color.GREEN to "OWNER"
                false -> Color.RED to "OTHER"
                null -> COLOR_PENDING to "대기"
            }

            boxPaint.color = color

            val left = face.x.toFloat()
            val top = face.y.toFloat()
            val right = (face.x + face.width).toFloat()
            val bottom = (face.y + face.height).toFloat()

            canvas.drawRect(left, top, right, bottom, boxPaint)

            val text = "ID:$trackId $roleLabel"
            val textY = (top - 8f).coerceAtLeast(textPaint.textSize)
            canvas.drawText(text, left, textY, textPaint)
        }

        // Bitmap → GL 텍스처 업로드
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, overlayTexId)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)

        return overlayTexId
    }

    fun release() {
        if (overlayTexId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(overlayTexId), 0)
            overlayTexId = 0
        }
        overlayBitmap?.recycle()
        overlayBitmap = null
        overlayCanvas = null
    }

    companion object {
        private const val COLOR_PENDING = 0xFFFFC107.toInt()
    }
}
