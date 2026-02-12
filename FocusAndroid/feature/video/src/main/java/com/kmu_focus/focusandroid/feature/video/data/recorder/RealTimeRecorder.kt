package com.kmu_focus.focusandroid.feature.video.data.recorder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GLSurfaceView 렌더 스레드에서 그려지는 FBO를 그대로 H.264 MP4로 인코딩하는 실시간 녹화기.
 *
 * - 입력: Surface 기반 H.264 인코더 (COLOR_FormatSurface)
 * - 출력: MediaMuxer를 통해 단일 비디오 트랙 MP4 파일
 * - 타임스탬프: GL 쪽에서 eglPresentationTimeANDROID로 전달 (ns)
 *
 * 오디오 트랙은 현재 미구현(구조만 열어둠).
 */
class RealTimeRecorder(
    private val encoderFactory: VideoEncoderFactory = DefaultVideoEncoderFactory(),
    private val muxerFactory: VideoMuxerFactory = DefaultVideoMuxerFactory(),
    private val loggerTag: String = "RealTimeRecorder",
    /** 테스트 등에서 백그라운드 drain 스레드를 끄고 싶을 때 false로 설정. */
    private val enableBackgroundDrain: Boolean = true,
) {

    @Volatile
    var isRecording: Boolean = false
        private set

    private val draining = AtomicBoolean(false)

    @Volatile
    private var encoder: VideoEncoder? = null

    @Volatile
    private var muxer: VideoMuxer? = null

    @Volatile
    private var drainThread: Thread? = null

    @Volatile
    private var muxerStarted = false

    @Volatile
    private var videoTrackIndex: Int = -1

    private val bufferInfo = MediaCodec.BufferInfo()

    /**
     * 비디오 인코더 + Muxer를 초기화하고, 인코더 입력 Surface를 콜백으로 전달한다.
     *
     * @param width 인코딩 해상도 (픽셀)
     * @param height 인코딩 해상도 (픽셀)
     * @param bitRate 비트레이트 (bps)
     * @param frameRate 목표 프레임레이트 (fps)
     * @param outputFile 출력 MP4 파일
     * @param onInputSurfaceReady GLSurfaceView에서 사용할 인코더 입력 Surface 콜백
     */
    @Synchronized
    fun start(
        width: Int,
        height: Int,
        outputFile: File,
        bitRate: Int = DEFAULT_BITRATE,
        frameRate: Int = DEFAULT_FRAME_RATE,
        onInputSurfaceReady: (Surface) -> Unit,
    ) {
        check(!isRecording) { "이미 녹화 중입니다" }

        val encoder = encoderFactory.create(
            width = width,
            height = height,
            bitRate = bitRate,
            frameRate = frameRate,
        )
        val muxer = muxerFactory.create(outputFile)

        val surface = encoder.createInputSurface()

        this.encoder = encoder
        this.muxer = muxer
        this.muxerStarted = false
        this.videoTrackIndex = -1

        isRecording = true
        draining.set(true)

        if (enableBackgroundDrain) {
            // 인코더 출력 draining 스레드 시작
            val thread = Thread({ drainLoop() }, "realtime-encoder-drain")
            thread.isDaemon = true
            thread.start()
            drainThread = thread
        }

        onInputSurfaceReady(surface)
    }

    /**
     * 더 이상 프레임을 보내지 않음을 인코더에 알리고, draining 스레드가 정리되도록 한다.
     * 반복 호출해도 안전하다.
     */
    @Synchronized
    fun stop() {
        if (!isRecording) return
        isRecording = false

        draining.set(false)
        try {
            encoder?.signalEndOfInputStream()
        } catch (e: Exception) {
            Log.w(loggerTag, "signalEndOfInputStream 실패", e)
        }

        // encoder / muxer 해제까지 기다리되, 영원히 블록하지 않도록 타임아웃
        val thread = drainThread
        if (thread != null && thread.isAlive) {
            try {
                thread.join(DRAIN_JOIN_TIMEOUT_MS)
            } catch (e: InterruptedException) {
                Log.w(loggerTag, "drainThread join 인터럽트", e)
            }
        }
    }

    private fun drainLoop() {
        val encoder = this.encoder
        val muxer = this.muxer

        if (encoder == null || muxer == null) {
            Log.w(loggerTag, "drainLoop: encoder 또는 muxer가 null입니다")
            releaseInternal()
            return
        }

        try {
            while (draining.get()) {
                val bufferIndex = encoder.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                when {
                    bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // 아직 출력 없음. isRecording 플래그를 보고 빠져나갈지 판단.
                        if (!isRecording && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break
                        }
                    }

                    bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // 비디오 트랙 등록 후 Muxer 시작
                        val newFormat = encoder.outputFormat
                        videoTrackIndex = muxer.addTrack(newFormat)
                        muxer.start()
                        muxerStarted = true
                    }

                    bufferIndex >= 0 -> {
                        val encodedData = encoder.getOutputBuffer(bufferIndex)
                        if (encodedData == null) {
                            encoder.releaseOutputBuffer(bufferIndex, false)
                            continue
                        }

                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }

                        if (bufferInfo.size > 0 && muxerStarted && videoTrackIndex >= 0) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                        }

                        val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        encoder.releaseOutputBuffer(bufferIndex, false)

                        if (isEos && !isRecording) {
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(loggerTag, "drainLoop 실패", e)
        } finally {
            releaseInternal()
        }
    }

    @Synchronized
    private fun releaseInternal() {
        try {
            encoder?.stopAndRelease()
        } catch (e: Exception) {
            Log.w(loggerTag, "encoder stop/release 실패", e)
        } finally {
            encoder = null
        }

        try {
            if (muxerStarted) {
                muxer?.stopAndRelease()
            } else {
                muxer?.releaseQuietly()
            }
        } catch (e: Exception) {
            Log.w(loggerTag, "muxer stop/release 실패", e)
        } finally {
            muxer = null
        }

        drainThread = null
        muxerStarted = false
        videoTrackIndex = -1
    }

    interface VideoEncoder {
        fun createInputSurface(): Surface
        fun dequeueOutputBuffer(info: MediaCodec.BufferInfo, timeoutUs: Long): Int
        fun getOutputBuffer(index: Int): ByteBuffer?
        val outputFormat: MediaFormat
        fun releaseOutputBuffer(index: Int, render: Boolean)
        fun signalEndOfInputStream()
        fun stopAndRelease()
    }

    fun interface VideoEncoderFactory {
        fun create(
            width: Int,
            height: Int,
            bitRate: Int,
            frameRate: Int,
        ): VideoEncoder
    }

    interface VideoMuxer {
        fun addTrack(format: MediaFormat): Int
        fun start()
        fun writeSampleData(trackIndex: Int, byteBuf: ByteBuffer, info: MediaCodec.BufferInfo)
        fun stopAndRelease()
        fun releaseQuietly()
    }

    fun interface VideoMuxerFactory {
        fun create(outputFile: File): VideoMuxer
    }

    private class DefaultVideoEncoderFactory : VideoEncoderFactory {
        override fun create(
            width: Int,
            height: Int,
            bitRate: Int,
            frameRate: Int,
        ): VideoEncoder {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, DEFAULT_I_FRAME_INTERVAL_SEC)
            }

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            return object : VideoEncoder {
                override fun createInputSurface(): Surface = codec.createInputSurface()

                override fun dequeueOutputBuffer(info: MediaCodec.BufferInfo, timeoutUs: Long): Int {
                    return codec.dequeueOutputBuffer(info, timeoutUs)
                }

                override fun getOutputBuffer(index: Int): ByteBuffer? {
                    return codec.getOutputBuffer(index)
                }

                override val outputFormat: MediaFormat
                    get() = codec.outputFormat

                override fun releaseOutputBuffer(index: Int, render: Boolean) {
                    codec.releaseOutputBuffer(index, render)
                }

                override fun signalEndOfInputStream() {
                    codec.signalEndOfInputStream()
                }

                override fun stopAndRelease() {
                    try {
                        codec.stop()
                    } finally {
                        codec.release()
                    }
                }
            }
        }
    }

    private class DefaultVideoMuxerFactory : VideoMuxerFactory {
        override fun create(outputFile: File): VideoMuxer {
            val muxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            )
            return object : VideoMuxer {
                override fun addTrack(format: MediaFormat): Int = muxer.addTrack(format)

                override fun start() {
                    muxer.start()
                }

                override fun writeSampleData(
                    trackIndex: Int,
                    byteBuf: ByteBuffer,
                    info: MediaCodec.BufferInfo,
                ) {
                    muxer.writeSampleData(trackIndex, byteBuf, info)
                }

                override fun stopAndRelease() {
                    try {
                        muxer.stop()
                    } finally {
                        muxer.release()
                    }
                }

                override fun releaseQuietly() {
                    try {
                        muxer.release()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_BITRATE = 10_000_000
        private const val DEFAULT_FRAME_RATE = 30
        private const val DEFAULT_I_FRAME_INTERVAL_SEC = 1
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val DRAIN_JOIN_TIMEOUT_MS = 2_000L
    }
}

