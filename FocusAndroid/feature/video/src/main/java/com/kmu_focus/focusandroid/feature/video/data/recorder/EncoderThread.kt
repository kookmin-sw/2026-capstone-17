package com.kmu_focus.focusandroid.feature.video.data.recorder

import android.opengl.EGL14
import android.opengl.EGLContext
import android.opengl.GLES30
import android.util.Log
import android.view.Surface
import com.kmu_focus.focusandroid.feature.video.data.gl.OESTextureProgram

/**
 * 인코더 렌더 전용 스레드.
 *
 * GL 렌더 스레드에서는 frame 신호만 넘기고, EGL makeCurrent + draw + swap은 모두 여기서 처리한다.
 */
open class EncoderThread(
    private val loggerTag: String = "EncoderThread",
) {

    private data class PendingFrame(
        val fboTextureId: Int,
        val fenceSync: Long,
        val timestampNs: Long,
        val width: Int,
        val height: Int,
    )

    private val frameLock = Object()

    @Volatile
    private var running = false

    @Volatile
    private var renderReady = false

    @Volatile
    private var workerThread: Thread? = null

    @Volatile
    private var frameWidth: Int = 0

    @Volatile
    private var frameHeight: Int = 0

    // frameLock으로 보호
    private var pendingFrame: PendingFrame? = null

    private var encoderEglSurface: EncoderEglSurface? = null
    private var textureProgram: OESTextureProgram? = null
    private var droppedBeforeReadyCount = 0
    private var renderedFrameCount = 0

    @Synchronized
    fun start(
        encoderInputSurface: Surface,
        sharedContext: EGLContext,
    ) {
        if (running) return
        require(sharedContext != EGL14.EGL_NO_CONTEXT) { "sharedContext가 유효하지 않습니다." }

        running = true
        renderReady = false
        droppedBeforeReadyCount = 0
        renderedFrameCount = 0
        workerThread = Thread(
            {
                runLoop(encoderInputSurface, sharedContext)
            },
            "encoder-render-thread",
        ).apply {
            isDaemon = true
            start()
        }
    }

    fun isRunning(): Boolean = running
    fun isRenderReady(): Boolean = running && renderReady

    @Synchronized
    fun stop() {
        running = false
        synchronized(frameLock) {
            frameLock.notifyAll()
        }

        val thread = workerThread
        if (thread != null && thread.isAlive && thread !== Thread.currentThread()) {
            try {
                thread.join(STOP_JOIN_TIMEOUT_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.w(loggerTag, "EncoderThread join interrupted", e)
            }
            if (thread.isAlive) {
                Log.w(loggerTag, "EncoderThread join timeout. force state reset.")
                thread.interrupt()
            }
        }

        // join 타임아웃 여부와 무관하게 호출자 관점 상태는 반드시 초기화한다.
        renderReady = false
        running = false
        workerThread = null
        clearPendingFrame(deleteFence = true)
    }

    fun submitFrame(
        fboTextureId: Int,
        fenceSync: Long,
        timestampNs: Long,
        width: Int = 0,
        height: Int = 0,
    ) {
        if (fenceSync == 0L) return
        if (!running) {
            deleteFenceQuietly(fenceSync)
            return
        }
        if (!renderReady) {
            droppedBeforeReadyCount++
            if (droppedBeforeReadyCount <= 3 || droppedBeforeReadyCount % 30 == 0) {
                Log.w(
                    loggerTag,
                    "submitFrame drop(not-ready): fbo=$fboTextureId size=${width}x$height dropped=$droppedBeforeReadyCount",
                )
            }
            deleteFenceQuietly(fenceSync)
            return
        }

        synchronized(frameLock) {
            pendingFrame?.let { stale ->
                // 최신 프레임만 유지 (백로그 누적 방지)
                deleteFenceQuietly(stale.fenceSync)
            }
            pendingFrame = PendingFrame(
                fboTextureId = fboTextureId,
                fenceSync = fenceSync,
                timestampNs = timestampNs,
                width = width,
                height = height,
            )
            frameLock.notifyAll()
        }
    }

    internal open fun makeCurrent() {
        encoderEglSurface?.makeCurrent()
    }

    internal open fun waitForFence(fenceSync: Long) {
        if (fenceSync == 0L) return
        try {
            var result = GLES30.glClientWaitSync(
                fenceSync,
                GLES30.GL_SYNC_FLUSH_COMMANDS_BIT,
                FENCE_WAIT_TIMEOUT_NS,
            )
            while (running && result == GLES30.GL_TIMEOUT_EXPIRED) {
                result = GLES30.glClientWaitSync(
                    fenceSync,
                    GLES30.GL_SYNC_FLUSH_COMMANDS_BIT,
                    FENCE_WAIT_TIMEOUT_NS,
                )
            }
            if (result == GLES30.GL_WAIT_FAILED) {
                Log.w(loggerTag, "glClientWaitSync 실패")
            }
        } finally {
            deleteFenceQuietly(fenceSync)
        }
    }

    internal open fun renderToEncoder(fboTextureId: Int) {
        val w = frameWidth
        val h = frameHeight
        if (w <= 0 || h <= 0) {
            Log.w(loggerTag, "renderToEncoder skip: invalid size ${w}x$h, fbo=$fboTextureId")
            return
        }
        if (textureProgram == null) {
            Log.w(loggerTag, "renderToEncoder skip: textureProgram is null, fbo=$fboTextureId")
            return
        }

        GLES30.glViewport(0, 0, w, h)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        textureProgram?.draw2D(fboTextureId)
        renderedFrameCount++
        if (renderedFrameCount <= 3 || renderedFrameCount % 30 == 0) {
            Log.w(loggerTag, "renderToEncoder done: fbo=$fboTextureId size=${w}x$h rendered=$renderedFrameCount")
        }
    }

    internal open fun swapWithTimestamp(timestampNs: Long) {
        val surface = encoderEglSurface
        if (surface == null) {
            Log.w(loggerTag, "swapWithTimestamp skip: encoderEglSurface is null")
            return
        }
        surface.setPresentationTime(timestampNs)
        val swapped = surface.swapBuffers()
        if (renderedFrameCount <= 3 || renderedFrameCount % 30 == 0) {
            Log.w(loggerTag, "swapWithTimestamp: ts=$timestampNs swapped=$swapped")
        }
        if (!swapped) {
            Log.w(loggerTag, "swapBuffers 실패")
        }
    }

    private fun runLoop(
        encoderInputSurface: Surface,
        sharedContext: EGLContext,
    ) {
        Log.w(loggerTag, "runLoop init start")
        try {
            encoderEglSurface = EncoderEglSurface(
                encoderInputSurface = encoderInputSurface,
                sharedContext = sharedContext,
            )
            makeCurrent()
            textureProgram = OESTextureProgram().apply { init() }
            renderReady = true
            Log.w(loggerTag, "runLoop init success: EGL + shader ready")
        } catch (e: Exception) {
            Log.e(loggerTag, "Encoder EGL 초기화 실패", e)
            running = false
            renderReady = false
            clearPendingFrame(deleteFence = true)
            releaseGlResources()
            return
        }

        try {
            while (true) {
                val frame = synchronized(frameLock) {
                    while (running && pendingFrame == null) {
                        frameLock.wait()
                    }
                    val current = pendingFrame
                    pendingFrame = null
                    current
                } ?: if (running) {
                    continue
                } else {
                    break
                }

                frameWidth = frame.width
                frameHeight = frame.height

                makeCurrent()
                waitForFence(frame.fenceSync)
                renderToEncoder(frame.fboTextureId)
                swapWithTimestamp(frame.timestampNs)
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            Log.e(loggerTag, "Encoder render loop 실패", e)
        } finally {
            clearPendingFrame(deleteFence = true)
            releaseGlResources()
            renderReady = false
            running = false
            workerThread = null
        }
    }

    private fun releaseGlResources() {
        try {
            textureProgram?.release()
        } catch (e: Exception) {
            Log.w(loggerTag, "textureProgram.release 실패", e)
        } finally {
            textureProgram = null
        }

        try {
            encoderEglSurface?.release()
        } catch (e: Exception) {
            Log.w(loggerTag, "encoderEglSurface.release 실패", e)
        } finally {
            encoderEglSurface = null
        }
    }

    private fun clearPendingFrame(deleteFence: Boolean) {
        val frame = synchronized(frameLock) {
            val current = pendingFrame
            pendingFrame = null
            current
        }

        if (deleteFence && frame != null) {
            deleteFenceQuietly(frame.fenceSync)
        }
    }

    private fun deleteFenceQuietly(fenceSync: Long) {
        if (fenceSync == 0L) return
        try {
            GLES30.glDeleteSync(fenceSync)
        } catch (_: Exception) {
        }
    }

    private companion object {
        private const val STOP_JOIN_TIMEOUT_MS = 2_000L
        private const val FENCE_WAIT_TIMEOUT_NS = 5_000_000L
    }
}
