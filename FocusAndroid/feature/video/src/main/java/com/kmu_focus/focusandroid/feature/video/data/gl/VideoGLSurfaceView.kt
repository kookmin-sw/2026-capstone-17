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

    fun release() {
        queueEvent { renderer.release() }
    }
}
