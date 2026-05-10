package com.kmu_focus.focusandroid.core.media.data.gl

import com.kmu_focus.focusandroid.core.ai.domain.entity.FaceLandmarks5
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import kotlin.math.max
import kotlin.math.sqrt

/**
 * ProcessedFrame의 얼굴/라벨 정보를 privacy blur 셰이더 입력 타원 목록으로 변환한다.
 * 랜드마크가 있으면 기존 랜드마크 기반 타원을 우선 사용하고, 없으면 박스 기반 원으로 fallback한다.
 * OWNER(true)는 제외하고, PENDING(null)/OTHER(false)만 포함한다.
 */
object FaceEllipseCalculator {

    private const val LANDMARK_PADDING_RATIO = 1.12f
    private const val LANDMARK_VERTICAL_RADIUS_RATIO = 1.35f
    private const val BOX_MASK_RADIUS_RATIO = 0.84f
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

            val face = frame.faces[index]
            result.add(face.landmarks?.toEllipse(frameWidth, frameHeight) ?: face.toFallbackEllipse(frameWidth, frameHeight))
        }

        return result
    }

    private fun FaceLandmarks5.toEllipse(
        frameWidth: Float,
        frameHeight: Float,
    ): EllipseParams {
        val eyeCenter = getEyeCenter()
        val mouthCenter = getMouthCenter()
        val eyeDistance = getEyeDistance()
        val eyeMouthDistance = sqrt(
            (mouthCenter.x - eyeCenter.x) * (mouthCenter.x - eyeCenter.x) +
                (mouthCenter.y - eyeCenter.y) * (mouthCenter.y - eyeCenter.y)
        )
        val centerX = eyeCenter.x + (mouthCenter.x - eyeCenter.x) * 0.3f
        val centerY = eyeCenter.y + (mouthCenter.y - eyeCenter.y) * 0.3f
        val radiusX = eyeDistance * LANDMARK_PADDING_RATIO
        val radiusY = eyeMouthDistance * LANDMARK_VERTICAL_RADIUS_RATIO * LANDMARK_PADDING_RATIO
        return EllipseParams(
            centerX = normalize(centerX, frameWidth),
            centerY = normalize(centerY, frameHeight),
            radiusX = normalize(radiusX, frameWidth),
            radiusY = normalize(radiusY, frameHeight),
            angle = getFaceAngle(),
        )
    }

    private fun com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace.toFallbackEllipse(
        frameWidth: Float,
        frameHeight: Float,
    ): EllipseParams {
        val centerX = x + width / 2f
        val centerY = y + height / 2f
        val expandedRadius = max(width, height) * BOX_MASK_RADIUS_RATIO
        return EllipseParams(
            centerX = normalize(centerX, frameWidth),
            centerY = normalize(centerY, frameHeight),
            radiusX = normalize(expandedRadius, frameWidth),
            radiusY = normalize(expandedRadius, frameHeight),
            angle = 0f,
        )
    }

    private fun normalize(value: Float, size: Float): Float {
        if (size <= 0f) return 0f
        return (value / size).coerceIn(0f, 1f)
    }
}
