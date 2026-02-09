package com.kmu_focus.focusandroid.core.ai.domain.config

data class DetectionConfig(
    val modelName: String = "yunet_face.onnx",
    val inputSize: Int = 480,
    val scoreThreshold: Float = 0.6f,
    val nmsThreshold: Float = 0.3f,
    val topK: Int = 5000,
    /** 테스트 프로젝트와 동일 0.5f (0.6f면 검출 누락으로 인식 기회 감소) */
    val confidenceThreshold: Float = 0.5f
)
