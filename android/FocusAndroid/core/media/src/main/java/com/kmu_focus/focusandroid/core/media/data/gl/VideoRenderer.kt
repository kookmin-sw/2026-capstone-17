package com.kmu_focus.focusandroid.core.media.data.gl

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.annotation.VisibleForTesting
import com.kmu_focus.focusandroid.core.media.data.recorder.EncoderThread
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import java.nio.ByteBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val PRIVACY_REGION_PADDING_RATIO = 1.18f
private const val PRIVACY_REGION_PADDING_PX = 6
private const val PRIVACY_BLUR_LONG_EDGE_MAX = 20f
private const val PRIVACY_BLUR_LONG_EDGE_MIN = 12f
private const val PRIVACY_BLUR_LONG_EDGE_NEAR_PX = 140f
private const val PRIVACY_BLUR_LONG_EDGE_FAR_PX = 320f
private const val PRIVACY_BLUR_MIN_EDGE = 8
private const val PRIVACY_BLUR_MAX_EDGE = 24
private const val PRIVACY_KAWASE_OFFSET_NEAR = 1.5f
private const val PRIVACY_KAWASE_OFFSET_FAR = 2.75f

/**
 * 비동기 파이프라인: FBO 렌더링 → PBO readback → 검출 콜백 → (프리뷰/인코더 분기).
 * 인코더에는 분석이 완료된 동일 프레임 텍스처만 전달해 privacy blur와 원본 프레임이 어긋나지 않게 유지한다.
 */
class VideoRenderer(
    private val onFrameCaptured: (ByteBuffer, Int, Int) -> ProcessedFrame,
    private val onSurfaceReady: (Surface) -> Unit,
    private val onRendererReleased: (() -> Unit)? = null,
    private val encoderThread: EncoderThread = EncoderThread(),
) : android.opengl.GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private var oesTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null

    private val texMatrix = FloatArray(16)
    private val finalTexMatrix = FloatArray(16)
    private val rotationMatrix = FloatArray(16)
    private val program = OESTextureProgram()
    private val privacyBlurProgram = MosaicProgram()
    private val pboReader = PBOReader()

    // 프리뷰/분석용 더블 버퍼 FBO (PBO readback과 같은 프레임을 유지)
    private val previewSourceFboIds = IntArray(2)
    private val previewSourceTextureIds = IntArray(2)
    private var previewSourceWriteIndex = 0
    private var previewDisplayTextureId = 0
    private var isPreviewSynchronizedToAnalysis = false

    // 인코더용 더블 버퍼 FBO (EncoderThread 읽기와 쓰기 충돌 방지)
    private val encoderFboIds = IntArray(2)
    private val encoderFboTextureIds = IntArray(2)
    private var encoderFboWriteIndex = 0

    // privacy blur용 저해상도 ping-pong FBO
    private val privacyBlurFboIds = IntArray(2)
    private val privacyBlurTextureIds = IntArray(2)
    private var privacyBlurWidth = 0
    private var privacyBlurHeight = 0
    private var viewWidth = 0
    private var viewHeight = 0
    private var renderContentScaleX = 1f
    private var renderContentScaleY = 1f

    @Volatile
    private var contentScaleDirty = true

    @Volatile
    private var frameAvailable = false

    @Volatile
    private var videoWidth = 0

    @Volatile
    private var videoHeight = 0

    @Volatile
    private var inputSurfaceWidth = 0

    @Volatile
    private var inputSurfaceHeight = 0

    @Volatile
    private var previewRotationDegrees = 0

    @Volatile
    private var isFrontLensFacing = false

    // --- 실시간 인코더 연동용 ---
    @Volatile
    private var encoderSurface: Surface? = null

    @Volatile
    private var encoderWidth: Int = 0

    @Volatile
    private var encoderHeight: Int = 0

    private var recordingEnabled: Boolean = false
    private var lastEncoderTimestampNs: Long = Long.MIN_VALUE
    private var lastAnalysisTimestampNs: Long = Long.MIN_VALUE
    private var lastFrameTimestampNs: Long = Long.MIN_VALUE

    /** 영상 해상도 설정 시 FBO에 fit(letter-box)로 렌더하여 종횡비 왜곡 제거. 0이면 보정 없음. */
    fun setVideoSize(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
        contentScaleDirty = true
    }

    fun setInputSurfaceSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        inputSurfaceWidth = width
        inputSurfaceHeight = height
        surfaceTexture?.setDefaultBufferSize(width, height)
    }

    fun setPreviewRotationDegrees(degrees: Int) {
        previewRotationDegrees = normalizeRotationDegrees(degrees)
    }

    fun setFrontLensFacing(isFront: Boolean) {
        isFrontLensFacing = isFront
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
            if (inputSurfaceWidth > 0 && inputSurfaceHeight > 0) {
                it.setDefaultBufferSize(inputSurfaceWidth, inputSurfaceHeight)
            }
        }
        surface = Surface(surfaceTexture)

        program.init()
        privacyBlurProgram.init()

        // GL 스레드 → 메인 스레드 전환
        val readySurface = surface!!
        mainHandler.post { onSurfaceReady(readySurface) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        contentScaleDirty = true
        GLES30.glViewport(0, 0, width, height)

        // 기존 FBO 및 픽셀 읽기 상태 정리
        pboReader.release()
        releaseFramebuffers()
        resetAnalysisState()

        // 프리뷰/분석용 더블 버퍼 FBO 생성
        GLES30.glGenTextures(2, previewSourceTextureIds, 0)
        GLES30.glGenFramebuffers(2, previewSourceFboIds, 0)
        for (i in 0..1) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, previewSourceTextureIds[i])
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,
                0,
                GLES30.GL_RGBA,
                width,
                height,
                0,
                GLES30.GL_RGBA,
                GLES30.GL_UNSIGNED_BYTE,
                null
            )
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, previewSourceFboIds[i])
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D,
                previewSourceTextureIds[i],
                0
            )
        }

        // 인코더용 더블 버퍼 FBO 생성
        GLES30.glGenTextures(2, encoderFboTextureIds, 0)
        GLES30.glGenFramebuffers(2, encoderFboIds, 0)
        for (i in 0..1) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, encoderFboTextureIds[i])
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, encoderFboIds[i])
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D,
                encoderFboTextureIds[i],
                0
            )
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        previewSourceWriteIndex = 0
        previewDisplayTextureId = 0
        encoderFboWriteIndex = 0

        if (width > 0 && height > 0) {
            pboReader.init(width, height)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        if (frameAvailable) {
            frameAvailable = false

            // 1. SurfaceTexture 업데이트
            surfaceTexture?.updateTexImage()
            surfaceTexture?.getTransformMatrix(texMatrix)
            val frameTimestampNs = surfaceTexture?.timestamp ?: 0L
            if (hasAnalysisTimestampReset(lastFrameTimestampNs, frameTimestampNs)) {
                resetAnalysisPipeline()
            }
            rememberFrameTimestamp(frameTimestampNs)
            val sourceTexMatrix = buildSourceTexMatrix(
                baseMatrix = texMatrix,
                rotationDegrees = previewRotationDegrees,
                isFrontLens = isFrontLensFacing,
            )

            // 2. OES → 프리뷰 FBO 렌더링
            if (contentScaleDirty) {
                updateRenderContentScale()
            }
            val scaleX = renderContentScaleX
            val scaleY = renderContentScaleY
            val currentPreviewBufferIndex = previewSourceWriteIndex
            val analysisPreviewBufferIndex = nextEncoderBufferIndex(currentPreviewBufferIndex)
            val currentPreviewTextureId = previewSourceTextureIds[currentPreviewBufferIndex]
            val analysisPreviewTextureId = previewSourceTextureIds[analysisPreviewBufferIndex]
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, previewSourceFboIds[currentPreviewBufferIndex])
            GLES30.glViewport(0, 0, viewWidth, viewHeight)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            program.drawOES(oesTextureId, sourceTexMatrix, scaleX, scaleY)

            // 3. PBO를 사용해 비동기 픽셀 읽기를 요청하고, 이전 프레임 결과만 분석한다.
            var processedFrame: ProcessedFrame? = null
            var encoderTextureIdForSubmit = 0
            val analysisBuffer = pboReader.readPixelsAsync()
            if (analysisBuffer != null && (recordingEnabled || shouldAnalyzeFrame(frameTimestampNs))) {
                processedFrame = onFrameCaptured(analysisBuffer, viewWidth, viewHeight)
                lastAnalysisTimestampNs = resolveAnalysisTimestampNs(frameTimestampNs)
            }
            val previewSelection = resolvePreviewFrameSelection(
                recordingEnabled = recordingEnabled,
                processedFrame = processedFrame,
                currentPreviewTextureId = currentPreviewTextureId,
                analysisPreviewTextureId = analysisPreviewTextureId,
                previousPreviewTextureId = previewDisplayTextureId,
                wasSynchronized = isPreviewSynchronizedToAnalysis,
            )
            previewDisplayTextureId = previewSelection.textureId
            isPreviewSynchronizedToAnalysis = previewSelection.isSynchronized
            previewSourceWriteIndex = analysisPreviewBufferIndex

            // 4. 인코더용 FBO: 저해상도 ROI + 2-pass Kawase blur를 합성한다.
            val frameForRecording = processedFrame
            if (recordingEnabled && frameForRecording != null) {
                encoderFboWriteIndex = nextEncoderBufferIndex(encoderFboWriteIndex)
                val ellipses = FaceEllipseCalculator.calculate(frameForRecording)
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, encoderFboIds[encoderFboWriteIndex])
                GLES30.glViewport(0, 0, viewWidth, viewHeight)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                privacyBlurProgram.copyTextureRegion(inputTexId = analysisPreviewTextureId)
                if (ellipses.isNotEmpty()) {
                    val blurRegion = calculatePrivacyBlurRegion(ellipses, viewWidth, viewHeight)
                    if (blurRegion != null) {
                        ensurePrivacyBlurBuffers(blurRegion.blurWidth, blurRegion.blurHeight)

                        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, privacyBlurFboIds[0])
                        GLES30.glViewport(0, 0, blurRegion.blurWidth, blurRegion.blurHeight)
                        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                        privacyBlurProgram.copyTextureRegion(
                            inputTexId = analysisPreviewTextureId,
                            sourceRect = blurRegion.regionRect,
                        )

                        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, privacyBlurFboIds[1])
                        GLES30.glViewport(0, 0, blurRegion.blurWidth, blurRegion.blurHeight)
                        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                        privacyBlurProgram.applyKawaseBlur(
                            inputTexId = privacyBlurTextureIds[0],
                            textureWidth = blurRegion.blurWidth,
                            textureHeight = blurRegion.blurHeight,
                            offsetPx = PRIVACY_KAWASE_OFFSET_NEAR,
                        )

                        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, privacyBlurFboIds[0])
                        GLES30.glViewport(0, 0, blurRegion.blurWidth, blurRegion.blurHeight)
                        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                        privacyBlurProgram.applyKawaseBlur(
                            inputTexId = privacyBlurTextureIds[1],
                            textureWidth = blurRegion.blurWidth,
                            textureHeight = blurRegion.blurHeight,
                            offsetPx = PRIVACY_KAWASE_OFFSET_FAR,
                        )

                        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, encoderFboIds[encoderFboWriteIndex])
                        GLES30.glViewport(0, 0, viewWidth, viewHeight)
                        privacyBlurProgram.compositeBlurredRegion(
                            inputTexId = privacyBlurTextureIds[0],
                            ellipses = ellipses,
                            regionRect = blurRegion.regionRect,
                        )
                    }
                }
                encoderTextureIdForSubmit = encoderFboTextureIds[encoderFboWriteIndex]
            }

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

            // 얼굴 미검출 프레임도 녹화는 지속한다.
            if (shouldSubmitFrameForRecording(recordingEnabled, processedFrame) && encoderTextureIdForSubmit != 0) {
                submitFrameToEncoderThread(
                    textureId = encoderTextureIdForSubmit,
                    frameTimestampNs = frameTimestampNs,
                )
            }
        }

        // 7. 프리뷰 FBO → 화면 렌더링 (검출 완료 후 표시)
        GLES30.glViewport(0, 0, viewWidth, viewHeight)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if (previewDisplayTextureId != 0) {
            program.draw2D(previewDisplayTextureId)
        }
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

        pboReader.release()
        privacyBlurProgram.release()
        program.release()
        releaseFramebuffers()
        if (oesTextureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
        }

        surface?.release()
        surfaceTexture?.release()
        surface = null
        surfaceTexture = null
        resetAnalysisState()

        onRendererReleased?.invoke()
    }

    private fun submitFrameToEncoderThread(
        textureId: Int,
        frameTimestampNs: Long,
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

        val encoderSourceScale = calculateEncoderSourceScale(
            sourceWidth = viewWidth,
            sourceHeight = viewHeight,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
        )

        GLES30.glFlush()
        encoderThread.submitFrame(
            fboTextureId = textureId,
            fenceSync = fenceSync,
            timestampNs = timestampNs,
            width = targetWidth,
            height = targetHeight,
            contentScaleX = encoderSourceScale.first,
            contentScaleY = encoderSourceScale.second,
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

    private fun shouldAnalyzeFrame(frameTimestampNs: Long): Boolean {
        if (lastAnalysisTimestampNs == Long.MIN_VALUE) {
            return true
        }
        val safeTimestampNs = resolveAnalysisTimestampNs(frameTimestampNs)
        return safeTimestampNs - lastAnalysisTimestampNs >= ANALYSIS_INTERVAL_NS
    }

    private fun resetAnalysisPipeline() {
        pboReader.resetPipeline()
        previewSourceWriteIndex = 0
        isPreviewSynchronizedToAnalysis = false
        lastAnalysisTimestampNs = Long.MIN_VALUE
        lastFrameTimestampNs = Long.MIN_VALUE
    }

    private fun resetAnalysisState() {
        lastAnalysisTimestampNs = Long.MIN_VALUE
        lastFrameTimestampNs = Long.MIN_VALUE
        previewDisplayTextureId = 0
        previewSourceWriteIndex = 0
        isPreviewSynchronizedToAnalysis = false
    }

    private fun rememberFrameTimestamp(frameTimestampNs: Long) {
        if (frameTimestampNs > 0L) {
            lastFrameTimestampNs = frameTimestampNs
        }
    }

    private fun resolveAnalysisTimestampNs(frameTimestampNs: Long): Long {
        return if (frameTimestampNs > 0L) {
            frameTimestampNs
        } else {
            System.nanoTime()
        }
    }

    private fun calculateEncoderSourceScale(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Pair<Float, Float> {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return 1f to 1f
        }

        val sourceAspect = sourceWidth.toFloat() / sourceHeight.toFloat()
        val targetAspect = targetWidth.toFloat() / targetHeight.toFloat()
        if (sourceAspect > targetAspect) {
            val scaleX = (targetAspect / sourceAspect).coerceIn(0f, 1f)
            return scaleX to 1f
        }
        val scaleY = (sourceAspect / targetAspect).coerceIn(0f, 1f)
        return 1f to scaleY
    }

    private fun buildSourceTexMatrix(
        baseMatrix: FloatArray,
        rotationDegrees: Int,
        isFrontLens: Boolean,
    ): FloatArray {
        val normalizedRotation = normalizeRotationDegrees(rotationDegrees)
        if (normalizedRotation == 0) {
            return baseMatrix
        }

        Matrix.setIdentityM(rotationMatrix, 0)
        Matrix.translateM(rotationMatrix, 0, 0.5f, 0.5f, 0f)
        val signedRotation = if (isFrontLens) -normalizedRotation else normalizedRotation
        Matrix.rotateM(rotationMatrix, 0, signedRotation.toFloat(), 0f, 0f, 1f)
        Matrix.translateM(rotationMatrix, 0, -0.5f, -0.5f, 0f)
        Matrix.multiplyMM(finalTexMatrix, 0, rotationMatrix, 0, baseMatrix, 0)
        return finalTexMatrix
    }

    private fun ensurePrivacyBlurBuffers(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (privacyBlurWidth == width && privacyBlurHeight == height && privacyBlurTextureIds.all { it != 0 }) {
            return
        }

        releasePrivacyBlurBuffers()

        GLES30.glGenTextures(2, privacyBlurTextureIds, 0)
        GLES30.glGenFramebuffers(2, privacyBlurFboIds, 0)
        for (i in 0..1) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, privacyBlurTextureIds[i])
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,
                0,
                GLES30.GL_RGBA,
                width,
                height,
                0,
                GLES30.GL_RGBA,
                GLES30.GL_UNSIGNED_BYTE,
                null,
            )
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, privacyBlurFboIds[i])
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D,
                privacyBlurTextureIds[i],
                0,
            )
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        privacyBlurWidth = width
        privacyBlurHeight = height
    }

    private fun releaseFramebuffers() {
        if (previewSourceFboIds[0] != 0 || previewSourceFboIds[1] != 0) {
            GLES30.glDeleteFramebuffers(2, previewSourceFboIds, 0)
            previewSourceFboIds[0] = 0
            previewSourceFboIds[1] = 0
        }
        if (previewSourceTextureIds[0] != 0 || previewSourceTextureIds[1] != 0) {
            GLES30.glDeleteTextures(2, previewSourceTextureIds, 0)
            previewSourceTextureIds[0] = 0
            previewSourceTextureIds[1] = 0
        }
        if (encoderFboIds[0] != 0 || encoderFboIds[1] != 0) {
            GLES30.glDeleteFramebuffers(2, encoderFboIds, 0)
            encoderFboIds[0] = 0
            encoderFboIds[1] = 0
        }
        if (encoderFboTextureIds[0] != 0 || encoderFboTextureIds[1] != 0) {
            GLES30.glDeleteTextures(2, encoderFboTextureIds, 0)
            encoderFboTextureIds[0] = 0
            encoderFboTextureIds[1] = 0
        }
        releasePrivacyBlurBuffers()
    }

    private fun releasePrivacyBlurBuffers() {
        if (privacyBlurFboIds[0] != 0 || privacyBlurFboIds[1] != 0) {
            GLES30.glDeleteFramebuffers(2, privacyBlurFboIds, 0)
            privacyBlurFboIds[0] = 0
            privacyBlurFboIds[1] = 0
        }
        if (privacyBlurTextureIds[0] != 0 || privacyBlurTextureIds[1] != 0) {
            GLES30.glDeleteTextures(2, privacyBlurTextureIds, 0)
            privacyBlurTextureIds[0] = 0
            privacyBlurTextureIds[1] = 0
        }
        privacyBlurWidth = 0
        privacyBlurHeight = 0
    }

    private companion object {
        private const val TAG = "VideoRenderer"
        private const val ANALYSIS_INTERVAL_NS = 50_000_000L

        private fun normalizeRotationDegrees(degrees: Int): Int {
            val normalized = ((degrees % 360) + 360) % 360
            return when (normalized) {
                90, 180, 270 -> normalized
                else -> 0
            }
        }
    }
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun shouldSubmitFrameForRecording(
    recordingEnabled: Boolean,
    processedFrame: ProcessedFrame?
): Boolean = recordingEnabled && processedFrame != null

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun hasAnalysisTimestampReset(lastFrameTimestampNs: Long, frameTimestampNs: Long): Boolean {
    return lastFrameTimestampNs != Long.MIN_VALUE &&
        frameTimestampNs > 0L &&
        frameTimestampNs < lastFrameTimestampNs
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun nextEncoderBufferIndex(currentIndex: Int): Int = 1 - currentIndex

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
data class PreviewFrameSelection(
    val textureId: Int,
    val isSynchronized: Boolean,
)

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun resolvePreviewFrameSelection(
    recordingEnabled: Boolean,
    processedFrame: ProcessedFrame?,
    currentPreviewTextureId: Int,
    analysisPreviewTextureId: Int,
    previousPreviewTextureId: Int,
    wasSynchronized: Boolean,
): PreviewFrameSelection {
    if (!recordingEnabled) {
        return PreviewFrameSelection(
            textureId = currentPreviewTextureId,
            isSynchronized = false,
        )
    }
    if (processedFrame != null && analysisPreviewTextureId != 0) {
        return PreviewFrameSelection(
            textureId = analysisPreviewTextureId,
            isSynchronized = true,
        )
    }
    if (wasSynchronized && previousPreviewTextureId != 0) {
        return PreviewFrameSelection(
            textureId = previousPreviewTextureId,
            isSynchronized = true,
        )
    }
    return PreviewFrameSelection(
        textureId = currentPreviewTextureId,
        isSynchronized = false,
    )
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
data class PrivacyBlurRegion(
    val regionRect: UvRect,
    val blurWidth: Int,
    val blurHeight: Int,
)

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun calculatePrivacyBlurRegion(
    ellipses: List<EllipseParams>,
    viewWidth: Int,
    viewHeight: Int,
): PrivacyBlurRegion? {
    if (ellipses.isEmpty() || viewWidth <= 0 || viewHeight <= 0) return null

    var leftPx = viewWidth
    var topPx = viewHeight
    var rightPx = 0
    var bottomPx = 0

    ellipses.forEach { ellipse ->
        val paddedRadiusX = ellipse.radiusX * PRIVACY_REGION_PADDING_RATIO
        val paddedRadiusY = ellipse.radiusY * PRIVACY_REGION_PADDING_RATIO
        val candidateLeft = floor((ellipse.centerX - paddedRadiusX) * viewWidth).toInt() - PRIVACY_REGION_PADDING_PX
        val candidateTop = floor((ellipse.centerY - paddedRadiusY) * viewHeight).toInt() - PRIVACY_REGION_PADDING_PX
        val candidateRight = ceil((ellipse.centerX + paddedRadiusX) * viewWidth).toInt() + PRIVACY_REGION_PADDING_PX
        val candidateBottom = ceil((ellipse.centerY + paddedRadiusY) * viewHeight).toInt() + PRIVACY_REGION_PADDING_PX

        leftPx = minOf(leftPx, candidateLeft)
        topPx = minOf(topPx, candidateTop)
        rightPx = maxOf(rightPx, candidateRight)
        bottomPx = maxOf(bottomPx, candidateBottom)
    }

    val clampedLeft = leftPx.coerceIn(0, viewWidth - 1)
    val clampedTop = topPx.coerceIn(0, viewHeight - 1)
    val clampedRight = rightPx.coerceIn(clampedLeft + 1, viewWidth)
    val clampedBottom = bottomPx.coerceIn(clampedTop + 1, viewHeight)
    val regionWidth = (clampedRight - clampedLeft).coerceAtLeast(1)
    val regionHeight = (clampedBottom - clampedTop).coerceAtLeast(1)
    val blurSize = resolvePrivacyBlurTextureSize(regionWidth, regionHeight)

    return PrivacyBlurRegion(
        regionRect = UvRect(
            minX = clampedLeft / viewWidth.toFloat(),
            minY = clampedTop / viewHeight.toFloat(),
            maxX = clampedRight / viewWidth.toFloat(),
            maxY = clampedBottom / viewHeight.toFloat(),
        ),
        blurWidth = blurSize.first,
        blurHeight = blurSize.second,
    )
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun resolvePrivacyBlurTextureSize(
    regionWidth: Int,
    regionHeight: Int,
): Pair<Int, Int> {
    if (regionWidth <= 0 || regionHeight <= 0) return PRIVACY_BLUR_MIN_EDGE to PRIVACY_BLUR_MIN_EDGE

    val longEdge = maxOf(regionWidth, regionHeight).toFloat()
    val scale = smoothstep(
        edge0 = PRIVACY_BLUR_LONG_EDGE_NEAR_PX,
        edge1 = PRIVACY_BLUR_LONG_EDGE_FAR_PX,
        value = longEdge,
    )
    val targetLongEdge = lerp(
        start = PRIVACY_BLUR_LONG_EDGE_MAX,
        end = PRIVACY_BLUR_LONG_EDGE_MIN,
        t = scale,
    )
    val downsampleScale = targetLongEdge / longEdge
    val blurWidth = quantizePrivacyBlurEdge((regionWidth * downsampleScale).roundToInt())
    val blurHeight = quantizePrivacyBlurEdge((regionHeight * downsampleScale).roundToInt())
    return blurWidth to blurHeight
}

private fun quantizePrivacyBlurEdge(value: Int): Int {
    val clamped = value.coerceIn(PRIVACY_BLUR_MIN_EDGE, PRIVACY_BLUR_MAX_EDGE)
    return if (clamped % 2 == 0) clamped else clamped + 1
}

private fun lerp(start: Float, end: Float, t: Float): Float {
    return start + (end - start) * t.coerceIn(0f, 1f)
}

private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
    if (edge0 == edge1) return 1f
    val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
