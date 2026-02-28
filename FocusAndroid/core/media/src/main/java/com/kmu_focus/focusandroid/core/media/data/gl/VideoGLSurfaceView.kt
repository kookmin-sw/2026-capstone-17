package com.kmu_focus.focusandroid.core.media.data.gl

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import com.kmu_focus.focusandroid.core.media.domain.entity.ProcessedFrame
import java.nio.ByteBuffer

class VideoGLSurfaceView(
    context: Context,
    private val onFrameCaptured: (ByteBuffer, Int, Int) -> ProcessedFrame,
    private val onSurfaceReady: (Surface) -> Unit,
    private val onRendererReleased: (() -> Unit)? = null,
) : GLSurfaceView(context) {

    private val renderer = VideoRenderer(
        onFrameCaptured = onFrameCaptured,
        onSurfaceReady = onSurfaceReady,
        onRendererReleased = onRendererReleased,
    )

    init {
        setEGLContextClientVersion(3)
        setRenderer(renderer)
        // onFrameAvailable 호출 시에만 렌더링
        renderMode = RENDERMODE_WHEN_DIRTY
        renderer.setGLSurfaceView(this)
    }

    /** 영상 해상도 설정 시 FBO에 fit 렌더하여 종횡비 왜곡 제거. 메인 스레드에서 호출. */
    fun setVideoSize(width: Int, height: Int) {
        renderer.setVideoSize(width, height)
    }

    /**
     * CameraX 등 외부 producer가 SurfaceTexture에 쓰는 버퍼 크기 힌트.
     * 카메라 프리뷰 품질을 위해 request 해상도와 맞춘다.
     */
    fun setInputSurfaceSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        queueEvent {
            renderer.setInputSurfaceSize(width, height)
        }
    }

    fun setPreviewRotationDegrees(degrees: Int) {
        queueEvent {
            renderer.setPreviewRotationDegrees(degrees)
        }
    }

    fun setFrontLensFacing(isFront: Boolean) {
        queueEvent {
            renderer.setFrontLensFacing(isFront)
        }
    }

    /** 인코더 입력 Surface 설정 (녹화용). 메인 스레드에서 호출 가능 (내부적으로 queueEvent 처리). */
    fun setEncoderSurface(
        surface: Surface?,
        width: Int = 0,
        height: Int = 0,
    ) {
        Log.w(TAG, "setEncoderSurface enqueue: surfaceNull=${surface == null}, size=${width}x$height")
        queueEvent {
            Log.w(TAG, "setEncoderSurface runOnGlThread: surfaceNull=${surface == null}, size=${width}x$height")
            renderer.setEncoderSurface(surface, width, height)
        }
    }

    fun release() {
        queueEvent { renderer.release() }
    }

    private companion object {
        private const val TAG = "VideoGLSurfaceView"
    }
}
