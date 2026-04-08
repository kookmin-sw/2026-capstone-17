package com.kmu_focus.focusandroid.core.ai.data.recognition

import android.graphics.Bitmap
import android.util.Log
import com.kmu_focus.focusandroid.core.ai.domain.detector.FaceDetector
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class OwnerEmbeddingStoreTest {

    private val faceDetector: FaceDetector = mockk(relaxed = true)
    private val embeddingExtractor: ArcFaceEmbeddingExtractor = mockk(relaxed = true)
    private lateinit var store: OwnerEmbeddingStore

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        store = OwnerEmbeddingStore(
            faceDetector = faceDetector,
            embeddingExtractor = embeddingExtractor,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `addOwnerFromEmbedding 호출 시 마스터 임베딩에 추가된다`() {
        val embedding = FloatArray(512) { 0.1f }

        val result = store.addOwnerFromEmbedding(embedding)

        assertTrue(result)
        assertEquals(1, store.getMasterEmbeddings().size)
        assertArrayEquals(embedding, store.getMasterEmbeddings()[0][0], 0.0001f)
    }

    @Test
    fun `addOwnerFromEmbedding 빈 배열이면 실패한다`() {
        val result = store.addOwnerFromEmbedding(FloatArray(0))

        assertFalse(result)
        assertTrue(store.getMasterEmbeddings().isEmpty())
    }

    @Test
    fun `addOwnerFromEmbedding과 addOwnerFromBitmap이 동일한 저장소에 누적된다`() {
        val embedding = FloatArray(512) { 0.2f }
        store.addOwnerFromEmbedding(embedding)

        assertEquals(1, store.getMasterEmbeddings().size)
    }

    @Test
    fun `addOwnerFromBitmap는 얼굴 미검출이어도 전체 이미지 임베딩 fallback으로 등록된다`() {
        val bitmap = mockk<Bitmap>(relaxed = true)
        val embedding = FloatArray(512) { 0.35f }
        every { faceDetector.detectFaces(bitmap) } returns emptyList()
        every { embeddingExtractor.extractEmbedding(bitmap) } returns embedding

        val result = store.addOwnerFromBitmap(bitmap)

        assertTrue(result)
        assertEquals(1, store.getMasterEmbeddings().size)
        assertArrayEquals(embedding, store.getMasterEmbeddings()[0][0], 0.0001f)
    }

    @Test
    fun `addOwnerFromEmbeddingWithOwnerId로 등록한 임베딩은 replaceOwnerEmbedding으로 교체된다`() {
        val ownerId = store.addOwnerFromEmbeddingWithOwnerId(FloatArray(512) { 0.2f })
        val replaced = store.replaceOwnerEmbedding(ownerId ?: -1, FloatArray(512) { 0.8f })

        assertTrue(replaced)
        assertArrayEquals(FloatArray(512) { 0.8f }, store.getMasterEmbeddings()[ownerId!!][0], 0.0001f)
    }

    @Test
    fun `replaceOwnerEmbedding은 잘못된 ownerId면 실패한다`() {
        val replaced = store.replaceOwnerEmbedding(999, FloatArray(512) { 0.3f })

        assertFalse(replaced)
    }

    @Test
    fun `clearOwners 시 임베딩 등록도 함께 삭제된다`() {
        store.addOwnerFromEmbedding(FloatArray(512) { 0.1f })
        assertEquals(1, store.getMasterEmbeddings().size)

        store.clearOwners()
        assertTrue(store.getMasterEmbeddings().isEmpty())
    }
}
