package com.kmu_focus.focusandroid.feature.video.data.processor

import android.graphics.Bitmap
import com.kmu_focus.focusandroid.feature.detection.domain.config.DetectionConfig
import com.kmu_focus.focusandroid.feature.detection.domain.detector.FaceDetector
import com.kmu_focus.focusandroid.feature.detection.domain.entity.DetectedFace
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FrameProcessorTest {

    private val faceDetector: FaceDetector = mockk()
    private val config = DetectionConfig()
    private val frameProcessor = FrameProcessor(faceDetector, config)

    @Test
    fun `process 호출 시 FaceDetector detectFaces를 호출함`() {
        val bitmap = mockk<Bitmap> {
            every { width } returns 640
            every { height } returns 480
        }
        every { faceDetector.detectFaces(bitmap) } returns emptyList()

        frameProcessor.process(bitmap, 1000L)

        verify(exactly = 1) { faceDetector.detectFaces(bitmap) }
    }

    @Test
    fun `검출된 얼굴이 ProcessedFrame에 담김`() {
        val bitmap = mockk<Bitmap> {
            every { width } returns 1920
            every { height } returns 1080
        }
        val faces = listOf(
            DetectedFace(10, 20, 100, 100, 0.95f),
            DetectedFace(200, 300, 80, 80, 0.85f)
        )
        every { faceDetector.detectFaces(bitmap) } returns faces

        val result = frameProcessor.process(bitmap, 2000L)

        assertEquals(2, result.faces.size)
        assertEquals(0.95f, result.faces[0].confidence, 0.001f)
    }

    @Test
    fun `검출 결과가 없으면 빈 리스트가 반환됨`() {
        val bitmap = mockk<Bitmap> {
            every { width } returns 640
            every { height } returns 480
        }
        every { faceDetector.detectFaces(bitmap) } returns emptyList()

        val result = frameProcessor.process(bitmap, 0L)

        assertTrue(result.faces.isEmpty())
    }

    @Test
    fun `frameWidth와 frameHeight가 Bitmap 크기와 일치함`() {
        val bitmap = mockk<Bitmap> {
            every { width } returns 1280
            every { height } returns 720
        }
        every { faceDetector.detectFaces(bitmap) } returns emptyList()

        val result = frameProcessor.process(bitmap, 500L)

        assertEquals(1280, result.frameWidth)
        assertEquals(720, result.frameHeight)
    }

    @Test
    fun `timestampMs가 정확히 전달됨`() {
        val bitmap = mockk<Bitmap> {
            every { width } returns 640
            every { height } returns 480
        }
        every { faceDetector.detectFaces(bitmap) } returns emptyList()

        val result = frameProcessor.process(bitmap, 12345L)

        assertEquals(12345L, result.timestampMs)
    }

    @Test
    fun `confidence가 임계값 미만인 얼굴은 필터링됨`() {
        val bitmap = mockk<Bitmap> {
            every { width } returns 1920
            every { height } returns 1080
        }
        val faces = listOf(
            DetectedFace(10, 20, 100, 100, 0.8f),
            DetectedFace(200, 300, 80, 80, 0.3f)
        )
        every { faceDetector.detectFaces(bitmap) } returns faces

        val result = frameProcessor.process(bitmap, 0L)

        assertEquals(1, result.faces.size)
        assertEquals(0.8f, result.faces[0].confidence, 0.001f)
    }

    @Test
    fun `커스텀 config의 confidenceThreshold가 적용됨`() {
        val customConfig = DetectionConfig(confidenceThreshold = 0.9f)
        val customProcessor = FrameProcessor(faceDetector, customConfig)
        val bitmap = mockk<Bitmap> {
            every { width } returns 640
            every { height } returns 480
        }
        val faces = listOf(
            DetectedFace(10, 20, 100, 100, 0.95f),
            DetectedFace(200, 300, 80, 80, 0.85f)
        )
        every { faceDetector.detectFaces(bitmap) } returns faces

        val result = customProcessor.process(bitmap, 0L)

        assertEquals(1, result.faces.size)
        assertEquals(0.95f, result.faces[0].confidence, 0.001f)
    }

    // --- ByteBuffer 오버로드 테스트 ---

    private fun createRGBABuffer(width: Int, height: Int): ByteBuffer {
        val size = width * height * 4
        return ByteBuffer.allocateDirect(size).apply {
            order(ByteOrder.nativeOrder())
            for (i in 0 until size) put(128.toByte())
            flip()
        }
    }

    private fun <T> withMockedBitmapFactory(width: Int, height: Int, block: () -> T): T {
        val mockBitmap = mockk<Bitmap>(relaxed = true) {
            every { this@mockk.width } returns width
            every { this@mockk.height } returns height
        }
        mockkStatic(Bitmap::class)
        every { Bitmap.createBitmap(width, height, any()) } returns mockBitmap
        return try {
            block()
        } finally {
            unmockkStatic(Bitmap::class)
        }
    }

    @Test
    fun `ByteBuffer process 호출 시 FaceDetector detectFaces를 호출함`() {
        val buffer = createRGBABuffer(640, 480)
        every { faceDetector.detectFaces(any<Bitmap>()) } returns emptyList()

        withMockedBitmapFactory(640, 480) {
            frameProcessor.process(buffer, 640, 480, 1000L)
        }

        verify(exactly = 1) { faceDetector.detectFaces(any<Bitmap>()) }
    }

    @Test
    fun `ByteBuffer process 결과의 frameWidth와 frameHeight가 파라미터와 일치함`() {
        val buffer = createRGBABuffer(1280, 720)
        every { faceDetector.detectFaces(any<Bitmap>()) } returns emptyList()

        val result = withMockedBitmapFactory(1280, 720) {
            frameProcessor.process(buffer, 1280, 720, 500L)
        }

        assertEquals(1280, result.frameWidth)
        assertEquals(720, result.frameHeight)
    }

    @Test
    fun `ByteBuffer process에서도 confidence 필터링이 적용됨`() {
        val buffer = createRGBABuffer(640, 480)
        val faces = listOf(
            DetectedFace(10, 20, 100, 100, 0.8f),
            DetectedFace(200, 300, 80, 80, 0.3f)
        )
        every { faceDetector.detectFaces(any<Bitmap>()) } returns faces

        val result = withMockedBitmapFactory(640, 480) {
            frameProcessor.process(buffer, 640, 480, 0L)
        }

        assertEquals(1, result.faces.size)
        assertEquals(0.8f, result.faces[0].confidence, 0.001f)
    }

    @Test
    fun `ByteBuffer process에서 timestampMs가 정확히 전달됨`() {
        val buffer = createRGBABuffer(640, 480)
        every { faceDetector.detectFaces(any<Bitmap>()) } returns emptyList()

        val result = withMockedBitmapFactory(640, 480) {
            frameProcessor.process(buffer, 640, 480, 99999L)
        }

        assertEquals(99999L, result.timestampMs)
    }
}
