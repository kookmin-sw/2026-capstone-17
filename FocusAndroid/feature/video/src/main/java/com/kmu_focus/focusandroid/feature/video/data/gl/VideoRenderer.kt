package com.kmu_focus.focusandroid.feature.video.data.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.EGL14
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.kmu_focus.focusandroid.feature.video.data.recorder.EncoderEglSurface
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

    @Volatile
    private var videoWidth = 0

    @Volatile
    private var videoHeight = 0

    private var drawCount = 0

    // --- 실시간 인코더 연동용 ---
    @Volatile
    private var encoderSurface: Surface? = null

    // GL 스레드에서만 접근해야 함
    private var encoderEglSurface: EncoderEglSurface? = null
    private var recordingEnabled: Boolean = false

    /** 영상 해상도 설정 시 FBO에 fit(letter-box)로 렌더하여 종횡비 왜곡 제거. 0이면 보정 없음. */
    fun setVideoSize(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
    }

    // ExoPlayer.setVideoSurface()는 메인 스레드에서만 호출 가능
    private val mainHandler = Handler(Looper.getMainLooper())

    // GLSurfaceView 참조 (requestRender용)
    private var glSurfaceViewRef: android.opengl.GLSurfaceView? = null

    fun setGLSurfaceView(view: android.opengl.GLSurfaceView) {
        glSurfaceViewRef = view
    }

    /**
     * RealTimeRecorder에서 전달된 인코더 입력 Surface 설정.
     *
     * - 반드시 GLSurfaceView.queueEvent를 통해 GL 스레드에서 호출해야 한다.
     * - null을 전달하면 녹화를 중지하고 EGLSurface를 정리한다.
     */
    fun setEncoderSurface(surface: Surface?) {
        if (surface == null) {
            recordingEnabled = false
            encoderSurface = null
            encoderEglSurface?.release()
            encoderEglSurface = null
        } else {
            encoderSurface = surface
            recordingEnabled = true
            // 실제 EncoderEglSurface 생성은 onDrawFrame 안에서, EGL 컨텍스트가 유효할 때 lazy하게 처리
        }
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
            if (drawCount++ % 60 == 0) {
                Log.i("VideoRenderer", "texMatrix (4x4 col-major): " +
                    "[${texMatrix[0]}, ${texMatrix[1]}, ${texMatrix[2]}, ${texMatrix[3]}], " +
                    "[${texMatrix[4]}, ${texMatrix[5]}, ${texMatrix[6]}, ${texMatrix[7]}], " +
                    "[${texMatrix[8]}, ${texMatrix[9]}, ${texMatrix[10]}, ${texMatrix[11]}], " +
                    "[${texMatrix[12]}, ${texMatrix[13]}, ${texMatrix[14]}, ${texMatrix[15]}]")
            }

            // 2. OES → FBO 렌더링 (종횡비 보정: 영상이 view에 fit되도록 content scale 적용 → letter-box, 원본 비율 유지)
            val (scaleX, scaleY) = if (videoWidth > 0 && videoHeight > 0) {
                val scale = minOf(viewWidth / videoWidth.toFloat(), viewHeight / videoHeight.toFloat())
                val contentW = videoWidth * scale
                val contentH = videoHeight * scale
                Pair(contentW / viewWidth, contentH / viewHeight)
            } else Pair(1f, 1f)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
            GLES30.glViewport(0, 0, viewWidth, viewHeight)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            program.drawOES(oesTextureId, texMatrix, scaleX, scaleY)

            // 3. glReadPixels 동기 읽기 (재사용 버퍼, GC 없음)
            readBuffer?.let { buf ->
                buf.clear()
                GLES30.glReadPixels(0, 0, viewWidth, viewHeight, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
                buf.rewind()

                // 4. 검출 콜백 (동기: 콜백 완료까지 화면 표시 보류)
                onFrameCaptured(buf, viewWidth, viewHeight)
            }

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

            // --- 인코더 Surface에도 동일한 FBO를 그려서 녹화 ---
            if (recordingEnabled && encoderSurface != null) {
                // 현재 GLSurfaceView의 EGL 상태 저장
                val eglDisplay = EGL14.eglGetCurrentDisplay()
                val eglDrawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
                val eglReadSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
                val eglContext = EGL14.eglGetCurrentContext()

                if (encoderEglSurface == null) {
                    // 인코더용 EGLSurface를 현재 컨텍스트 기준으로 생성 (GL 스레드에서만 가능)
                    encoderEglSurface = try {
                        EncoderEglSurface(encoderSurface!!)
                    } catch (e: Exception) {
                        Log.e("VideoRenderer", "EncoderEglSurface 생성 실패", e)
                        recordingEnabled = false
                        null
                    }
                }

                encoderEglSurface?.let { encSurface ->
                    // 인코더 EGLSurface를 current로 전환
                    encSurface.makeCurrent()

                    // FBO 텍스처를 인코더 Surface에 전체 화면으로 렌더
                    GLES30.glViewport(0, 0, viewWidth, viewHeight)
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                    program.draw2D(fboTextureId)

                    // 타임스탬프는 ns 단위. 여기서는 System.nanoTime() 사용.
                    encSurface.setPresentationTime(System.nanoTime())
                    encSurface.swapBuffers()

                    // 다시 GLSurfaceView의 원래 EGLSurface로 복귀
                    EGL14.eglMakeCurrent(eglDisplay, eglDrawSurface, eglReadSurface, eglContext)
                }
            }
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
        recordingEnabled = false
        encoderSurface = null
        encoderEglSurface?.release()
        encoderEglSurface = null

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
