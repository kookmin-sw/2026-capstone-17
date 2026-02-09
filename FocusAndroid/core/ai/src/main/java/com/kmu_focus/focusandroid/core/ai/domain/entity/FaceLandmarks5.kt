package com.kmu_focus.focusandroid.core.ai.domain.entity

data class Point2f(val x: Float, val y: Float)

data class FaceLandmarks5(
    val rightEye: Point2f,
    val leftEye: Point2f,
    val nose: Point2f,
    val rightMouth: Point2f,
    val leftMouth: Point2f
) {
    fun getEyeCenter(): Point2f = Point2f(
        (rightEye.x + leftEye.x) / 2f,
        (rightEye.y + leftEye.y) / 2f
    )

    fun getEyeDistance(): Float {
        val dx = rightEye.x - leftEye.x
        val dy = rightEye.y - leftEye.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    fun getMouthCenter(): Point2f = Point2f(
        (rightMouth.x + leftMouth.x) / 2f,
        (rightMouth.y + leftMouth.y) / 2f
    )

    /** 얼굴 기울기 각도 (라디안). 테스트 프로젝트와 동일. */
    fun getFaceAngle(): Float {
        val dx = leftEye.x - rightEye.x
        val dy = leftEye.y - rightEye.y
        return kotlin.math.atan2(dy, dx)
    }

    /**
     * 정면 응시 여부 (YuNet 5점 기준, 테스트와 동일).
     * 코가 두 눈 중심에서 벗어난 정도로 대칭 판단. symmetryThreshold 미만이면 정면.
     */
    fun isFrontal(symmetryThreshold: Float = 0.2f): Boolean {
        val eyeCenterX = (leftEye.x + rightEye.x) / 2f
        val eyeDist = getEyeDistance()
        if (eyeDist < 1e-6f) return false
        val noseOffset = kotlin.math.abs(nose.x - eyeCenterX) / eyeDist
        return noseOffset < symmetryThreshold
    }
}
