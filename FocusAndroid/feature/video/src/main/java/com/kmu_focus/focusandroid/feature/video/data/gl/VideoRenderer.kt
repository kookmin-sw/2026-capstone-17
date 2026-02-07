package com.kmu_focus.focusandroid.feature.video.data.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.os.Handler
import android.os.Looper
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 동기 파이프라인: FBO 렌더링 → glReadPixels(동기) → 검출 콜백 → 화면 표시.
 * 검출 완료 후에 화면을 그리므로 박스와 영상이 완벽 동기화.
 * YuNet ~10-15ms + glReadPixels ~1-2ms = 30fps(33ms) 예산 이내.
 */
class VideoRenderer(
    private val onSurfaceReady: (Surface) -> Unit,
    private val onFrameCaptured: (ByteBuffer, Int, Int) -> Unit
) : android.opengl.GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private var oesTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null

    private val texMatrix = FloatArray(16)
    private val program = OESTextureProgram()

    // FBO (싱글)
    private var fboId = 0
    private var fboTextureId = 0
    private var viewWidth = 0
    private var viewHeight = 0

    // glReadPixels용 재사용 버퍼 (GC 방지)
    private var readBuffer: ByteBuffer? = null

    @Volatile
    private var frameAvailable = false

    // ExoPlayer.setVideoSurface()는 메인 스레드에서만 호출 가능
    private val mainHandler = Handler(Looper.getMainLooper())

    // GLSurfaceView 참조 (requestRender용)
    private var glSurfaceViewRef: android.opengl.GLSurfaceView? = null

    fun setGLSurfaceView(view: android.opengl.GLSurfaceView) {
        glSurfaceViewRef = view
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)

        // OES 텍스처 생성
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        oesTextureId = texIds[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        // SurfaceTexture → Surface → ExoPlayer에 전달
        surfaceTexture = SurfaceTexture(oesTextureId).also {
            it.setOnFrameAvailableListener(this)
        }
        surface = Surface(surfaceTexture)

        program.init()

        // GL 스레드 → 메인 스레드 전환
        val readySurface = surface!!
        mainHandler.post { onSurfaceReady(readySurface) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        GLES30.glViewport(0, 0, width, height)

        // 기존 FBO 정리
        if (fboId != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
            GLES30.glDeleteTextures(1, intArrayOf(fboTextureId), 0)
        }

        // FBO 텍스처 생성
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        fboTextureId = texIds[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTextureId)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)

        // FBO 생성 및 텍스처 연결
        val fboIds = IntArray(1)
        GLES30.glGenFramebuffers(1, fboIds, 0)
        fboId = fboIds[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, fboTextureId, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        // glReadPixels용 버퍼 재할당
        readBuffer = ByteBuffer.allocateDirect(width * height * 4).apply {
            order(ByteOrder.nativeOrder())
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        if (frameAvailable) {
            frameAvailable = false

            // 1. SurfaceTexture 업데이트
            surfaceTexture?.updateTexImage()
            surfaceTexture?.getTransformMatrix(texMatrix)

            // 2. OES → FBO 렌더링
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
            GLES30.glViewport(0, 0, viewWidth, viewHeight)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            program.drawOES(oesTextureId, texMatrix)

            // 3. glReadPixels 동기 읽기 (재사용 버퍼, GC 없음)
            readBuffer?.let { buf ->
                buf.clear()
                GLES30.glReadPixels(0, 0, viewWidth, viewHeight, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
                buf.rewind()

                // 4. 검출 콜백 (동기: 콜백 완료까지 화면 표시 보류)
                onFrameCaptured(buf, viewWidth, viewHeight)
            }

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        }

        // 5. FBO → 화면 렌더링 (검출 완료 후 표시)
        GLES30.glViewport(0, 0, viewWidth, viewHeight)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        program.draw2D(fboTextureId)
    }

    override fun onFrameAvailable(st: SurfaceTexture?) {
        frameAvailable = true
        glSurfaceViewRef?.requestRender()
    }

    fun release() {
        program.release()

        if (fboId != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
            GLES30.glDeleteTextures(1, intArrayOf(fboTextureId), 0)
        }
        if (oesTextureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
        }

        surface?.release()
        surfaceTexture?.release()
        surface = null
        surfaceTexture = null
        readBuffer = null
    }
}
