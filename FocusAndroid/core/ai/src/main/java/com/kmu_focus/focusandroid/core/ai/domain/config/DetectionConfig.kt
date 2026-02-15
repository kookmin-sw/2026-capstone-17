package com.kmu_focus.focusandroid.core.ai.domain.config

data class DetectionConfig(
    val modelName: String = "yunet_face.onnx",
    val inputSize: Int = 360,
    val scoreThreshold: Float = 0.6f,
    val nmsThreshold: Float = 0.3f,
    val topK: Int = 5000,
    /** 파이프라인 기본값: score/confidence 모두 0.6 */
    val confidenceThreshold: Float = 0.6f
)
