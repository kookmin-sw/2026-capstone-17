package com.kmu_focus.focusandroid.feature.video.data.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 더블 버퍼링 PBO로 glReadPixels 비동기 수행.
 * writeIndex PBO에 현재 프레임 읽기 요청 → readIndex PBO에서 이전 프레임 수거.
 * 1프레임 지연 발생 (30fps 기준 ~33ms, 검출 정확도에 무의미).
 */
class PBOReader {

    private val pboIds = IntArray(2)
    private var width = 0
    private var height = 0
    private var bufferSize = 0

    // 더블 버퍼링 인덱스
    private var writeIndex = 0
    private val readIndex get() = 1 - writeIndex

    private var initialized = false
    private var firstFrameSkipped = false

    fun init(w: Int, h: Int) {
        width = w
        height = h
        bufferSize = w * h * 4 // RGBA

        GLES30.glGenBuffers(2, pboIds, 0)
        for (i in 0..1) {
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pboIds[i])
            GLES30.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER, bufferSize, null, GLES30.GL_STREAM_READ)
        }
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)

        initialized = true
        firstFrameSkipped = false
    }

    /**
     * 비동기 픽셀 읽기.
     * @return 이전 프레임의 RGBA ByteBuffer, 첫 프레임은 null (1프레임 지연)
     */
    fun readPixelsAsync(): ByteBuffer? {
        if (!initialized) return null

        // writeIndex PBO에 현재 FBO 내용 비동기 읽기 요청
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pboIds[writeIndex])
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, 0)

        var result: ByteBuffer? = null

        if (firstFrameSkipped) {
            // readIndex PBO에서 이전 프레임 수거 (DMA 완료된 데이터)
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pboIds[readIndex])
            val mapped = GLES30.glMapBufferRange(
                GLES30.GL_PIXEL_PACK_BUFFER, 0, bufferSize, GLES30.GL_MAP_READ_BIT
            ) as? ByteBuffer

            if (mapped != null) {
                // GC 방지: direct buffer에 복사
                result = ByteBuffer.allocateDirect(bufferSize).apply {
                    order(ByteOrder.nativeOrder())
                    put(mapped)
                    flip()
                }
                GLES30.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER)
            }
        } else {
            firstFrameSkipped = true
        }

        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)

        // 인덱스 스왑
        writeIndex = readIndex

        return result
    }

    fun release() {
        if (initialized) {
            GLES30.glDeleteBuffers(2, pboIds, 0)
            initialized = false
        }
    }
}
