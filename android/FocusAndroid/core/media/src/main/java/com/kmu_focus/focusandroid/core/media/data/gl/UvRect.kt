package com.kmu_focus.focusandroid.core.media.data.gl

/**
 * privacy blur 셰이더에서 쓰는 정규화 UV 사각형.
 *
 * 모든 값은 0.0~1.0 범위를 기준으로 전달한다.
 */
data class UvRect(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
) {
    companion object {
        val FULL = UvRect(
            minX = 0f,
            minY = 0f,
            maxX = 1f,
            maxY = 1f,
        )
    }
}
