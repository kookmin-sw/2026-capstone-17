package com.kmu_focus.focusandroid.feature.video.data.gl

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.Surface
import java.nio.ByteBuffer

class VideoGLSurfaceView(
    context: Context,
    private val onSurfaceReady: (Surface) -> Unit,
    private val onFrameCaptured: (ByteBuffer, Int, Int) -> Unit
) : GLSurfaceView(context) {

    private val renderer = VideoRenderer(
        onSurfaceReady = onSurfaceReady,
        onFrameCaptured = onFrameCaptured
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

    fun release() {
        queueEvent { renderer.release() }
    }
}
