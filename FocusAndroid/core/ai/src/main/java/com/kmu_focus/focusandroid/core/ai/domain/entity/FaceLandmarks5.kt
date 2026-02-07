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

    fun getFaceAngle(): Float {
        val dx = leftEye.x - rightEye.x
        val dy = leftEye.y - rightEye.y
        return Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }

    fun isFrontal(threshold: Float = 15f): Boolean {
        return kotlin.math.abs(getFaceAngle()) < threshold
    }
}
