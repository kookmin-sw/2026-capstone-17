package com.kmu_focus.focusandroid.feature.video.data.gl

import android.opengl.EGLSurface
import android.view.Surface

/**
 * EGLSurface 래퍼. 인코더 InputSurface(Window) 또는 Pbuffer를 감싸서
 * makeCurrent / setPresentationTime / swapBuffers 호출을 단순화.
 */
class OffscreenSurface private constructor(
    private val eglCore: EglCore,
    private val eglSurface: EGLSurface
) {
    companion object {
        /** MediaCodec 인코더의 InputSurface용 */
        fun fromWindow(eglCore: EglCore, surface: Surface): OffscreenSurface {
            return OffscreenSurface(eglCore, eglCore.createWindowSurface(surface))
        }

        /** FBO 전용 오프스크린(Pbuffer)용 */
        fun fromPbuffer(eglCore: EglCore, width: Int, height: Int): OffscreenSurface {
            return OffscreenSurface(eglCore, eglCore.createOffscreenSurface(width, height))
        }
    }

    fun makeCurrent() {
        eglCore.makeCurrent(eglSurface)
    }

    fun setPresentationTime(nsecs: Long) {
        eglCore.setPresentationTime(eglSurface, nsecs)
    }

    fun swapBuffers(): Boolean {
        return eglCore.swapBuffers(eglSurface)
    }

    fun release() {
        eglCore.releaseSurface(eglSurface)
    }
}
