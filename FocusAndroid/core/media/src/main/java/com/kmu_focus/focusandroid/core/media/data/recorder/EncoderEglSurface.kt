package com.kmu_focus.focusandroid.core.media.data.recorder

import android.opengl.EGLContext
import android.view.Surface
import com.kmu_focus.focusandroid.core.media.data.gl.EglCore
import com.kmu_focus.focusandroid.core.media.data.gl.OffscreenSurface

/**
 * MediaCodec 입력 Surface를 전용 EGL 컨텍스트에 바인딩한다.
 *
 * - sharedContext와 리소스(텍스처/버퍼)를 공유한다.
 * - 실제 makeCurrent/swapBuffers 호출은 Encoder 전용 스레드에서 수행한다.
 */
class EncoderEglSurface(
    private val encoderInputSurface: Surface,
    sharedContext: EGLContext,
) {

    private val eglCore: EglCore = EglCore(sharedContext = sharedContext)
    private val windowSurface: OffscreenSurface = OffscreenSurface.fromWindow(eglCore, encoderInputSurface)

    fun makeCurrent() = windowSurface.makeCurrent()

    fun setPresentationTime(nsecs: Long) {
        windowSurface.setPresentationTime(nsecs)
    }

    fun swapBuffers(): Boolean {
        return windowSurface.swapBuffers()
    }

    fun release() {
        try {
            windowSurface.release()
        } finally {
            eglCore.release()
        }
    }
}
