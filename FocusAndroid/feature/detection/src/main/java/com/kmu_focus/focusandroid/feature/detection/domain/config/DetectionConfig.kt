package com.kmu_focus.focusandroid.feature.detection.domain.config

data class DetectionConfig(
    val modelName: String = "yunet_face.onnx",
    val inputSize: Int = 320,
    val scoreThreshold: Float = 0.5f,
    val nmsThreshold: Float = 0.3f,
    val topK: Int = 5000,
    val confidenceThreshold: Float = 0.5f
)
