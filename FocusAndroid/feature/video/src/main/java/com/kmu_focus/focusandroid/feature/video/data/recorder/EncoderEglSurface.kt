package com.kmu_focus.focusandroid.feature.video.data.recorder

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.EGLExt
import android.view.Surface

/**
 * GLSurfaceView가 관리하는 현재 EGL 컨텍스트를 그대로 사용하여
 * MediaCodec 인코더 입력 Surface를 EGLSurface로 래핑한다.
 *
 * - 반드시 GLSurfaceView 렌더 스레드에서 생성/사용해야 한다.
 * - 컨텍스트는 공유가 아니라 "현재 컨텍스트를 그대로" 사용한다.
 */
class EncoderEglSurface(
    private val encoderInputSurface: Surface,
) {

    private val eglDisplay: EGLDisplay
    private val eglContext: EGLContext
    private val eglConfig: EGLConfig
    private val eglSurface: EGLSurface

    init {
        eglDisplay = EGL14.eglGetCurrentDisplay()
        eglContext = EGL14.eglGetCurrentContext()
        require(eglDisplay != EGL14.EGL_NO_DISPLAY) { "현재 EGLDisplay가 없습니다. GL 스레드에서 호출해야 합니다." }
        require(eglContext != EGL14.EGL_NO_CONTEXT) { "현재 EGLContext가 없습니다. GL 스레드에서 호출해야 합니다." }

        eglConfig = findCurrentConfig(eglDisplay, eglContext)
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay,
            eglConfig,
            encoderInputSurface,
            intArrayOf(EGL14.EGL_NONE),
            0,
        )
        require(eglSurface != EGL14.EGL_NO_SURFACE) { "인코더용 EGLSurface 생성 실패" }
    }

    fun makeCurrent() {
        check(
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext),
        ) { "EncoderEglSurface eglMakeCurrent 실패" }
    }

    fun setPresentationTime(nsecs: Long) {
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs)
    }

    fun swapBuffers(): Boolean {
        return EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    fun release() {
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
    }

    private fun findCurrentConfig(
        display: EGLDisplay,
        context: EGLContext,
    ): EGLConfig {
        val values = IntArray(1)
        val success = EGL14.eglQueryContext(
            display,
            context,
            EGL14.EGL_CONFIG_ID,
            values,
            0,
        )
        require(success) { "eglQueryContext(EGL_CONFIG_ID) 실패" }
        val configId = values[0]

        // 현재 display에서 사용 가능한 config들을 열거한 뒤 CONFIG_ID로 역추적
        val numConfigs = IntArray(1)
        // 첫 호출에서는 전체 개수만 얻기 위해 configs=null, config_size=0
        EGL14.eglGetConfigs(display, null, 0, 0, numConfigs, 0)
        val total = numConfigs[0]
        val configs = arrayOfNulls<EGLConfig>(total)
        // 실제 config 목록 조회
        EGL14.eglGetConfigs(display, configs, 0, total, numConfigs, 0)

        configs.forEach { cfg ->
            if (cfg != null) {
                val cfgValues = IntArray(1)
                val ok = EGL14.eglGetConfigAttrib(
                    display,
                    cfg,
                    EGL14.EGL_CONFIG_ID,
                    cfgValues,
                    0,
                )
                if (ok && cfgValues[0] == configId) {
                    return cfg
                }
            }
        }

        error("현재 컨텍스트에 해당하는 EGLConfig를 찾을 수 없습니다 (configId=$configId)")
    }
}

