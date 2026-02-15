package com.kmu_focus.focusandroid.feature.video.data.gl

/**
 * 셰이더 입력용 정규화 타원 파라미터.
 *
 * 모든 좌표/반경은 0.0~1.0 범위를 기준으로 전달한다.
 * angle은 라디안 단위다.
 */
data class EllipseParams(
    val centerX: Float,
    val centerY: Float,
    val radiusX: Float,
    val radiusY: Float,
    val angle: Float
)
