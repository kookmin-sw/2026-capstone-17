package com.kmu_focus.focusandroid.feature.detection.domain.config

import org.junit.Assert.*
import org.junit.Test

class DetectionConfigTest {

    @Test
    fun `기본값이 테스트 프로젝트와 동일함`() {
        val config = DetectionConfig()

        assertEquals("yunet_face.onnx", config.modelName)
        assertEquals(320, config.inputSize)
        assertEquals(0.6f, config.scoreThreshold, 0.001f)
        assertEquals(0.3f, config.nmsThreshold, 0.001f)
        assertEquals(5000, config.topK)
        assertEquals(0.6f, config.confidenceThreshold, 0.001f)
    }

    @Test
    fun `커스텀 값으로 생성 가능함`() {
        val config = DetectionConfig(
            modelName = "custom_model.onnx",
            inputSize = 640,
            scoreThreshold = 0.7f,
            nmsThreshold = 0.4f,
            topK = 1000,
            confidenceThreshold = 0.8f
        )

        assertEquals("custom_model.onnx", config.modelName)
        assertEquals(640, config.inputSize)
        assertEquals(0.7f, config.scoreThreshold, 0.001f)
        assertEquals(0.4f, config.nmsThreshold, 0.001f)
        assertEquals(1000, config.topK)
        assertEquals(0.8f, config.confidenceThreshold, 0.001f)
    }

    @Test
    fun `data class 동등성 비교가 작동함`() {
        val config1 = DetectionConfig()
        val config2 = DetectionConfig()
        assertEquals(config1, config2)
    }

    @Test
    fun `copy를 통한 부분 변경이 작동함`() {
        val config = DetectionConfig()
        val modified = config.copy(scoreThreshold = 0.8f)

        assertEquals(0.8f, modified.scoreThreshold, 0.001f)
        assertEquals(config.modelName, modified.modelName)
        assertEquals(config.inputSize, modified.inputSize)
    }
}
