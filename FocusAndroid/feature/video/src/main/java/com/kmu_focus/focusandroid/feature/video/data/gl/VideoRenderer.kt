package com.kmu_focus.focusandroid.feature.video.data.gl

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.kmu_focus.focusandroid.feature.video.data.recorder.EncoderThread
import com.kmu_focus.focusandroid.feature.video.domain.entity.ProcessedFrame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 동기 파이프라인: FBO 렌더링 → glReadPixels(동기) → 검출 콜백 → 오버레이 합성 → 화면/인코더.
 * 검출 완료 후에 화면을 그리므로 박스와 영상이 완벽 동기화.
 * 실시간 녹화 시 FBO에 박스를 합성하여 인코더에 저장 (트랜스코딩과 동일).
 */
class VideoRenderer(
    private val onFrameCaptured: (ByteBuffer, Int, Int) -> ProcessedFrame,
    private val onSurfaceReady: (Surface) -> Unit,
    private val encoderThread: EncoderThread = EncoderThread(),
) : android.opengl.GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private var oesTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null

    private val texMatrix = FloatArray(16)
    private val program = OESTextureProgram()
    private val mosaicProgram = MosaicProgram()
    private val overlayRenderer = OverlayRenderer()

    // FBO 더블 버퍼 (EncoderThread가 읽는 동안 덮어쓰기 방지)
    private val fboIds = IntArray(2)
    private val fboTextureIds = IntArray(2)
    private var fboIndex = 0
    private var viewWidth = 0
    private var viewHeight = 0
    private var renderContentScaleX = 1f
    private var renderContentScaleY = 1f

    @Volatile
    private var contentScaleDirty = true

    // glReadPixels용 재사용 버퍼 (GC 방지)
    private var readBuffer: ByteBuffer? = null

    @Volatile
    private var frameAvailable = false

    @Volatile
    private var videoWidth = 0

    @Volatile
    private var videoHeight = 0

    // --- 실시간 인코더 연동용 ---
    @Volatile
    private var encoderSurface: Surface? = null

    @Volatile
    private var encoderWidth: Int = 0

    @Volatile
    private var encoderHeight: Int = 0

    private var recordingEnabled: Boolean = false
    private var lastEncoderTimestampNs: Long = Long.MIN_VALUE

    /** 영상 해상도 설정 시 FBO에 fit(letter-box)로 렌더하여 종횡비 왜곡 제거. 0이면 보정 없음. */
    fun setVideoSize(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
        contentScaleDirty = true
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
    fun setEncoderSurface(
        surface: Surface?,
        width: Int = 0,
        height: Int = 0,
    ) {
        if (surface == null) {
            recordingEnabled = false
            encoderSurface = null
            encoderWidth = 0
            encoderHeight = 0
            lastEncoderTimestampNs = Long.MIN_VALUE
            encoderThread.stop()
        } else {
            if (encoderSurface !== surface) {
                encoderThread.stop()
                lastEncoderTimestampNs = Long.MIN_VALUE
            }
            encoderSurface = surface
            encoderWidth = width
            encoderHeight = height
            recordingEnabled = true
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
        mosaicProgram.init()

        // GL 스레드 → 메인 스레드 전환
        val readySurface = surface!!
        mainHandler.post { onSurfaceReady(readySurface) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        contentScaleDirty = true
        GLES30.glViewport(0, 0, width, height)

        // 기존 FBO 및 오버레이 정리
        overlayRenderer.release()
        if (fboIds[0] != 0) {
            GLES30.glDeleteFramebuffers(2, fboIds, 0)
            GLES30.glDeleteTextures(2, fboTextureIds, 0)
            fboIds[0] = 0
            fboIds[1] = 0
            fboTextureIds[0] = 0
            fboTextureIds[1] = 0
        }

        // FBO 더블 버퍼 생성
        GLES30.glGenTextures(2, fboTextureIds, 0)
        GLES30.glGenFramebuffers(2, fboIds, 0)
        for (i in 0..1) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTextureIds[i])
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[i])
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, fboTextureIds[i], 0)
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        // glReadPixels용 버퍼 재할당
        readBuffer = ByteBuffer.allocateDirect(width * height * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        if (width > 0 && height > 0) {
            overlayRenderer.init(width, height)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        if (frameAvailable) {
            frameAvailable = false

            // 1. SurfaceTexture 업데이트
            surfaceTexture?.updateTexImage()
            surfaceTexture?.getTransformMatrix(texMatrix)
            val frameTimestampNs = surfaceTexture?.timestamp ?: 0L

            // 2. OES → FBO 렌더링 (더블 버퍼 교대)
            fboIndex = 1 - fboIndex
            if (contentScaleDirty) {
                updateRenderContentScale()
            }
            val scaleX = renderContentScaleX
            val scaleY = renderContentScaleY
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[fboIndex])
            GLES30.glViewport(0, 0, viewWidth, viewHeight)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            program.drawOES(oesTextureId, texMatrix, scaleX, scaleY)

            // 3. glReadPixels 동기 읽기 (재사용 버퍼, GC 없음)
            var processedFrame: ProcessedFrame? = null
            readBuffer?.let { buf ->
                buf.clear()
                GLES30.glReadPixels(0, 0, viewWidth, viewHeight, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
                buf.rewind()

                // 4. 검출 콜백 (동기: 콜백 완료까지 화면 표시 보류)
                val frame = onFrameCaptured(buf, viewWidth, viewHeight)
                processedFrame = frame

                // 5. 검출 직후 모자이크 패스 적용 (타원 대상이 있으면 반대편 FBO로 스왑)
                val ellipses = FaceEllipseCalculator.calculate(frame)
                if (ellipses.isNotEmpty()) {
                    val srcTexture = fboTextureIds[fboIndex]
                    val dstFboIdx = 1 - fboIndex
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[dstFboIdx])
                    GLES30.glViewport(0, 0, viewWidth, viewHeight)
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                    mosaicProgram.draw(
                        inputTexId = srcTexture,
                        ellipses = ellipses,
                        blockSize = MOSAIC_BLOCK_SIZE_PX,
                        viewWidth = viewWidth,
                        viewHeight = viewHeight
                    )
                    fboIndex = dstFboIdx
                }

                // 6. 검출 결과를 FBO 위에 알파 블렌딩 (실시간 녹화 시 인코더에 박스 포함)
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[fboIndex])
                if (frame.faces.isNotEmpty()) {
                    val overlayTexId = overlayRenderer.drawOverlay(frame)
                    if (overlayTexId != 0) {
                        program.draw2DBlend(overlayTexId)
                    }
                }
            }

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

            // 얼굴 미검출 프레임도 녹화는 지속한다.
            if (shouldSubmitFrameForRecording(recordingEnabled, processedFrame)) {
                submitFrameToEncoderThread(
                    textureId = fboTextureIds[fboIndex],
                    frameTimestampNs = frameTimestampNs,
                    contentScaleX = scaleX,
                    contentScaleY = scaleY,
                )
            }
        }

        // 6. FBO → 화면 렌더링 (검출 완료 후 표시)
        GLES30.glViewport(0, 0, viewWidth, viewHeight)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        program.draw2D(fboTextureIds[fboIndex])
    }

    override fun onFrameAvailable(st: SurfaceTexture?) {
        frameAvailable = true
        glSurfaceViewRef?.requestRender()
    }

    fun release() {
        recordingEnabled = false
        encoderSurface = null
        encoderWidth = 0
        encoderHeight = 0
        encoderThread.stop()

        overlayRenderer.release()
        mosaicProgram.release()
        program.release()

        if (fboIds[0] != 0) {
            GLES30.glDeleteFramebuffers(2, fboIds, 0)
            GLES30.glDeleteTextures(2, fboTextureIds, 0)
            fboIds[0] = 0
            fboIds[1] = 0
            fboTextureIds[0] = 0
            fboTextureIds[1] = 0
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

    private fun submitFrameToEncoderThread(
        textureId: Int,
        frameTimestampNs: Long,
        contentScaleX: Float,
        contentScaleY: Float,
    ) {
        val targetSurface = encoderSurface ?: return
        val targetWidth = encoderWidth
        val targetHeight = encoderHeight
        if (targetWidth <= 0 || targetHeight <= 0) {
            Log.w(TAG, "encoder size invalid: ${targetWidth}x$targetHeight")
            return
        }

        ensureEncoderThreadStarted(targetSurface)
        if (!encoderThread.isRenderReady()) return

        val fenceSync = GLES30.glFenceSync(GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
        if (fenceSync == 0L) {
            Log.w(TAG, "glFenceSync 생성 실패")
            return
        }

        val baseTimestampNs = if (frameTimestampNs > 0L) frameTimestampNs else System.nanoTime()
        val timestampNs = if (lastEncoderTimestampNs == Long.MIN_VALUE) {
            baseTimestampNs
        } else {
            maxOf(baseTimestampNs, lastEncoderTimestampNs + 1_000L)
        }
        lastEncoderTimestampNs = timestampNs

        GLES30.glFlush()
        encoderThread.submitFrame(
            fboTextureId = textureId,
            fenceSync = fenceSync,
            timestampNs = timestampNs,
            width = targetWidth,
            height = targetHeight,
            contentScaleX = contentScaleX,
            contentScaleY = contentScaleY,
        )
    }

    private fun ensureEncoderThreadStarted(surface: Surface) {
        if (encoderThread.isRunning()) {
            if (encoderThread.isRenderReady()) return
            Log.w(TAG, "EncoderThread running but not ready. restart 시도")
            encoderThread.stop()
        }

        val sharedContext = EGL14.eglGetCurrentContext()
        if (sharedContext == EGL14.EGL_NO_CONTEXT) {
            Log.w(TAG, "shared EGLContext가 없어 EncoderThread 시작을 건너뜁니다.")
            return
        }

        try {
            encoderThread.start(
                encoderInputSurface = surface,
                sharedContext = sharedContext,
            )
        } catch (e: Exception) {
            recordingEnabled = false
            Log.e(TAG, "EncoderThread 시작 실패", e)
        }
    }

    private fun updateRenderContentScale() {
        renderContentScaleX = 1f
        renderContentScaleY = 1f

        if (videoWidth > 0 && videoHeight > 0 && viewWidth > 0 && viewHeight > 0) {
            val scale = minOf(viewWidth / videoWidth.toFloat(), viewHeight / videoHeight.toFloat())
            val contentW = videoWidth * scale
            val contentH = videoHeight * scale
            renderContentScaleX = contentW / viewWidth.toFloat()
            renderContentScaleY = contentH / viewHeight.toFloat()
        }
        contentScaleDirty = false
    }

    private companion object {
        private const val TAG = "VideoRenderer"
        private const val MOSAIC_BLOCK_SIZE_PX = 16f
    }
}

internal fun shouldSubmitFrameForRecording(
    recordingEnabled: Boolean,
    processedFrame: ProcessedFrame?
): Boolean = recordingEnabled && processedFrame != null
