package com.kmu_focus.focusandroid.feature.video.data.processor

import android.graphics.Bitmap
import com.kmu_focus.focusandroid.feature.detection.domain.detector.FaceDetector
import com.kmu_focus.focusandroid.feature.detection.domain.entity.DetectedFace
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test

class FrameProcessorTest {

    private val faceDetector: FaceDetector = mockk()
    private val frameProcessor = FrameProcessor(faceDetector)

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
}
