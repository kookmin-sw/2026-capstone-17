package com.kmu_focus.focusandroid.feature.video.data.gl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.view.Surface

/**
 * GLSurfaceView 없이 EGL 컨텍스트를 관리.
 * 백그라운드 트랜스코딩 시 인코더 Surface에 OpenGL 렌더링을 위해 사용.
 */
class EglCore(sharedContext: EGLContext = EGL14.EGL_NO_CONTEXT) {

    val eglDisplay: EGLDisplay
    val eglContext: EGLContext
    val eglConfig: EGLConfig

    init {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "EGL display 획득 실패" }

        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            "EGL 초기화 실패"
        }

        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(
            EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)
        ) { "EGL config 선택 실패" }
        eglConfig = configs[0]!!

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(
            eglDisplay, eglConfig, sharedContext, contextAttribs, 0
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "EGL context 생성 실패" }
    }

    fun createWindowSurface(surface: Surface): EGLSurface {
        val attribs = intArrayOf(EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, attribs, 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "EGL window surface 생성 실패" }
        return eglSurface
    }

    fun createOffscreenSurface(width: Int, height: Int): EGLSurface {
        val attribs = intArrayOf(
            EGL14.EGL_WIDTH, width,
            EGL14.EGL_HEIGHT, height,
            EGL14.EGL_NONE
        )
        val eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, attribs, 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "EGL offscreen surface 생성 실패" }
        return eglSurface
    }

    fun makeCurrent(surface: EGLSurface) {
        check(EGL14.eglMakeCurrent(eglDisplay, surface, surface, eglContext)) {
            "eglMakeCurrent 실패"
        }
    }

    fun swapBuffers(surface: EGLSurface): Boolean {
        return EGL14.eglSwapBuffers(eglDisplay, surface)
    }

    fun setPresentationTime(surface: EGLSurface, nsecs: Long) {
        EGLExt.eglPresentationTimeANDROID(eglDisplay, surface, nsecs)
    }

    fun releaseSurface(surface: EGLSurface) {
        EGL14.eglDestroySurface(eglDisplay, surface)
    }

    fun release() {
        EGL14.eglMakeCurrent(
            eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
        )
        EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglTerminate(eglDisplay)
    }
}
