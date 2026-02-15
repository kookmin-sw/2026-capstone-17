package com.kmu_focus.focusandroid.feature.video.data.gl

import com.kmu_focus.focusandroid.feature.video.domain.entity.ProcessedFrame
import kotlin.math.sqrt

/**
 * ProcessedFrame의 얼굴/라벨 정보를 모자이크 셰이더 입력 타원 목록으로 변환한다.
 * OWNER(true)는 제외하고, PENDING(null)/OTHER(false)만 포함한다.
 */
object FaceEllipseCalculator {

    private const val PADDING_RATIO = 1.05f
    private const val RADIUS_X_MULTIPLIER = 1.0f
    private const val RADIUS_Y_MULTIPLIER = 1.35f
    private const val MAX_ELLIPSES = 8

    fun calculate(frame: ProcessedFrame): List<EllipseParams> {
        if (frame.faces.isEmpty()) return emptyList()
        if (frame.frameWidth <= 0 || frame.frameHeight <= 0) return emptyList()

        val result = ArrayList<EllipseParams>(minOf(frame.faces.size, MAX_ELLIPSES))
        val frameWidth = frame.frameWidth.toFloat()
        val frameHeight = frame.frameHeight.toFloat()

        for (index in frame.faces.indices) {
            if (result.size >= MAX_ELLIPSES) break

            val label = frame.faceLabels.getOrNull(index)
            if (label == true) continue

            val landmarks = frame.faces[index].landmarks ?: continue
            val eyeCenter = landmarks.getEyeCenter()
            val mouthCenter = landmarks.getMouthCenter()
            val eyeDistance = landmarks.getEyeDistance()
            val eyeMouthDistance = distance(
                x1 = eyeCenter.x,
                y1 = eyeCenter.y,
                x2 = mouthCenter.x,
                y2 = mouthCenter.y
            )

            val centerX = eyeCenter.x + (mouthCenter.x - eyeCenter.x) * 0.3f
            val centerY = eyeCenter.y + (mouthCenter.y - eyeCenter.y) * 0.3f
            val radiusX = eyeDistance * RADIUS_X_MULTIPLIER * PADDING_RATIO
            val radiusY = eyeMouthDistance * RADIUS_Y_MULTIPLIER * PADDING_RATIO
            val angle = landmarks.getFaceAngle()

            result.add(
                EllipseParams(
                    centerX = normalize(centerX, frameWidth),
                    centerY = normalize(centerY, frameHeight),
                    radiusX = normalize(radiusX, frameWidth),
                    radiusY = normalize(radiusY, frameHeight),
                    angle = angle
                )
            )
        }

        return result
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    private fun normalize(value: Float, size: Float): Float {
        if (size <= 0f) return 0f
        return (value / size).coerceIn(0f, 1f)
    }
}
