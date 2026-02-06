package com.kmu_focus.focusandroid.feature.detection.data.detector

import com.kmu_focus.focusandroid.feature.detection.domain.entity.DetectedFace
import com.kmu_focus.focusandroid.feature.detection.domain.entity.FaceLandmarks5
import com.kmu_focus.focusandroid.feature.detection.domain.entity.Point2f

// YuNet 15-column 출력을 DetectedFace 리스트로 변환하는 순수 Kotlin 함수
// OpenCV 의존성 없이 단위 테스트 가능하도록 Mat에서 분리
internal fun parseFaceOutput(
    rows: List<FloatArray>,
    scaleX: Float,
    scaleY: Float
): List<DetectedFace> {
    return rows.map { data ->
        val landmarks = FaceLandmarks5(
            rightEye = Point2f(data[4] * scaleX, data[5] * scaleY),
            leftEye = Point2f(data[6] * scaleX, data[7] * scaleY),
            nose = Point2f(data[8] * scaleX, data[9] * scaleY),
            rightMouth = Point2f(data[10] * scaleX, data[11] * scaleY),
            leftMouth = Point2f(data[12] * scaleX, data[13] * scaleY)
        )

        DetectedFace(
            x = (data[0] * scaleX).toInt(),
            y = (data[1] * scaleY).toInt(),
            width = (data[2] * scaleX).toInt(),
            height = (data[3] * scaleY).toInt(),
            confidence = data[14],
            landmarks = landmarks
        )
    }
}
