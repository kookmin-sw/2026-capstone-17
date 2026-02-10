package com.kmu_focus.focusandroid.feature.video.data.transcoder

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.util.Log
import com.kmu_focus.focusandroid.feature.video.data.gl.EglCore
import com.kmu_focus.focusandroid.feature.video.data.gl.OESTextureProgram
import com.kmu_focus.focusandroid.feature.video.data.gl.OffscreenSurface
import com.kmu_focus.focusandroid.feature.video.data.gl.OverlayRenderer
import com.kmu_focus.focusandroid.feature.video.data.processor.FrameProcessor
import com.kmu_focus.focusandroid.feature.video.domain.usecase.TranscodeProgress
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

private const val TAG = "VideoTranscoder"
private const val TIMEOUT_US = 10_000L
private const val ENCODER_BITRATE = 10_000_000
private const val ENCODER_FRAME_RATE = 30
private const val ENCODER_I_FRAME_INTERVAL = 1

class VideoTranscoder(
    private val context: Context,
    private val frameProcessor: FrameProcessor
) {
    // EGL 컨텍스트는 스레드에 바인딩되므로 단일 전용 스레드에서 실행
    private val transcodeDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "transcode-gl").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    /**
     * sourceUri 비디오를 디코딩→모델 추론→인코딩하여 outputFile에 저장.
     * 오디오 트랙은 그대로 복사(mux).
     */
    fun transcode(sourceUri: String, outputFile: File): Flow<TranscodeProgress> = flow {
        val uri = Uri.parse(sourceUri)

        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        val videoTrackIndex = findTrack(extractor, "video/")
        if (videoTrackIndex < 0) {
            emit(TranscodeProgress.Error("비디오 트랙을 찾을 수 없습니다"))
            return@flow
        }

        extractor.selectTrack(videoTrackIndex)
        val inputFormat = extractor.getTrackFormat(videoTrackIndex)
        val width = inputFormat.getInteger(MediaFormat.KEY_WIDTH)
        val height = inputFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val durationUs = inputFormat.getLong(MediaFormat.KEY_DURATION)
        val rotation = inputFormat.getIntegerSafe(MediaFormat.KEY_ROTATION, 0)

        // 회전 적용된 실제 프레임 크기
        val (frameWidth, frameHeight) = if (rotation == 90 || rotation == 270) {
            height to width
        } else {
            width to height
        }

        Log.i(TAG, "입력: ${width}x${height}, 회전: $rotation, 프레임: ${frameWidth}x${frameHeight}, 길이: ${durationUs / 1_000_000}s")

        // --- 인코더 설정 ---
        val encoderFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, frameWidth, frameHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, ENCODER_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, ENCODER_FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, ENCODER_I_FRAME_INTERVAL)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val encoderInputSurface = encoder.createInputSurface()
        encoder.start()

        // --- EGL 컨텍스트 (인코더 Surface에 그리기 위해) ---
        val eglCore = EglCore()
        val encoderSurface = OffscreenSurface.fromWindow(eglCore, encoderInputSurface)
        encoderSurface.makeCurrent()

        // --- OES 텍스처 + SurfaceTexture (디코더 출력 수신용) ---
        val oesTexId = createOESTexture()
        val decoderSurfaceTexture = SurfaceTexture(oesTexId)
        decoderSurfaceTexture.setDefaultBufferSize(width, height)
        val decoderOutputSurface = android.view.Surface(decoderSurfaceTexture)

        // --- 셰이더 프로그램 ---
        val program = OESTextureProgram()
        program.init()

        // --- FBO (glReadPixels로 프레임 읽기 + 모델 추론용) ---
        val fboId = IntArray(1)
        val fboTexId = IntArray(1)
        GLES30.glGenTextures(1, fboTexId, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexId[0])
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, frameWidth, frameHeight, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glGenFramebuffers(1, fboId, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId[0])
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, fboTexId[0], 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        // 검출 결과 오버레이 렌더러 (Canvas → Bitmap → GL 텍스처)
        val overlayRenderer = OverlayRenderer()
        overlayRenderer.init(frameWidth, frameHeight)

        // glReadPixels용 재사용 버퍼
        val readBuffer = ByteBuffer.allocateDirect(frameWidth * frameHeight * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        // --- 디코더 설정 ---
        val decoder = MediaCodec.createDecoderByType(
            inputFormat.getString(MediaFormat.KEY_MIME)!!
        )
        decoder.configure(inputFormat, decoderOutputSurface, null, 0)
        decoder.start()

        // --- Muxer ---
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerVideoTrackIndex = -1
        var muxerAudioTrackIndex = -1
        var muxerStarted = false

        // 오디오 트랙 탐색 (별도 Extractor로 복사)
        val audioExtractor = MediaExtractor()
        audioExtractor.setDataSource(context, uri, null)
        val audioTrackIndex = findTrack(audioExtractor, "audio/")
        if (audioTrackIndex >= 0) {
            audioExtractor.selectTrack(audioTrackIndex)
        }

        val texMatrix = FloatArray(16)
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var decoderDone = false
        var frameIndex = 0
        var lastProgressEmit = 0f

        try {
            while (!decoderDone) {
                // --- 디코더에 입력 공급 ---
                if (!inputDone) {
                    val inIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // --- 디코더 출력 처리 ---
                val outIdx = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outIdx >= 0) {
                    val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    val presentationTimeUs = bufferInfo.presentationTimeUs

                    if (bufferInfo.size > 0) {
                        // SurfaceTexture로 렌더링 후 릴리즈
                        decoder.releaseOutputBuffer(outIdx, true)
                        decoderSurfaceTexture.updateTexImage()
                        decoderSurfaceTexture.getTransformMatrix(texMatrix)

                        // OES → FBO 렌더링 (Y 반전 + content scale 1:1)
                        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId[0])
                        GLES30.glViewport(0, 0, frameWidth, frameHeight)
                        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                        program.drawOES(oesTexId, texMatrix, 1f, 1f)

                        // glReadPixels → FrameProcessor 추론
                        readBuffer.clear()
                        GLES30.glReadPixels(0, 0, frameWidth, frameHeight, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, readBuffer)
                        readBuffer.rewind()
                        val timestampMs = presentationTimeUs / 1000
                        val processedFrame = frameProcessor.process(readBuffer, frameWidth, frameHeight, timestampMs, frameIndex)

                        // 검출 결과를 FBO 위에 알파 블렌딩으로 오버레이
                        if (processedFrame.faces.isNotEmpty()) {
                            val overlayTexId = overlayRenderer.drawOverlay(processedFrame)
                            program.draw2DBlend(overlayTexId)
                        }

                        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

                        // FBO(원본 + 오버레이 합성) → 인코더 Surface에 렌더
                        GLES30.glViewport(0, 0, frameWidth, frameHeight)
                        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                        program.draw2D(fboTexId[0])

                        // 명시적 타임스탬프 전파 (싱크 밀림 방지)
                        encoderSurface.setPresentationTime(presentationTimeUs * 1000)
                        encoderSurface.swapBuffers()

                        frameIndex++

                        // 진행률 방출
                        if (durationUs > 0) {
                            val progress = (presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f)
                            if (progress - lastProgressEmit >= 0.01f) {
                                emit(TranscodeProgress.InProgress(progress))
                                lastProgressEmit = progress
                            }
                        }
                    } else {
                        decoder.releaseOutputBuffer(outIdx, false)
                    }

                    if (isEos) {
                        decoderDone = true
                        encoder.signalEndOfInputStream()
                    }
                }

                // --- 인코더 출력 → Muxer ---
                drainEncoder(encoder, muxer, bufferInfo, muxerStarted, muxerVideoTrackIndex, audioExtractor, audioTrackIndex).let { (started, vIdx, aIdx) ->
                    muxerStarted = started
                    muxerVideoTrackIndex = vIdx
                    muxerAudioTrackIndex = aIdx
                }
            }

            // 인코더 잔여 출력 모두 drain
            drainEncoderFinal(encoder, muxer, bufferInfo, muxerStarted, muxerVideoTrackIndex)

            // 오디오 mux
            if (muxerStarted && audioTrackIndex >= 0 && muxerAudioTrackIndex >= 0) {
                muxAudioTrack(audioExtractor, muxer, muxerAudioTrackIndex)
            }

            muxer.stop()
            emit(TranscodeProgress.Complete(outputFile.absolutePath))

        } catch (e: Exception) {
            Log.e(TAG, "트랜스코딩 실패", e)
            emit(TranscodeProgress.Error(e.message ?: "트랜스코딩 실패"))
        } finally {
            // MediaCodec은 Surface보다 먼저 중지/해제해야 BufferQueue 에러를 피할 수 있다.
            try {
                decoder.stop()
            } catch (e: Exception) {
                Log.w(TAG, "decoder.stop 실패", e)
            }
            try {
                decoder.release()
            } catch (e: Exception) {
                Log.w(TAG, "decoder.release 실패", e)
            }
            try {
                encoder.stop()
            } catch (e: Exception) {
                Log.w(TAG, "encoder.stop 실패", e)
            }
            try {
                encoder.release()
            } catch (e: Exception) {
                Log.w(TAG, "encoder.release 실패", e)
            }
            try {
                muxer.release()
            } catch (e: Exception) {
                Log.w(TAG, "muxer.release 실패", e)
            }
            try {
                extractor.release()
            } catch (e: Exception) {
                Log.w(TAG, "extractor.release 실패", e)
            }
            try {
                audioExtractor.release()
            } catch (e: Exception) {
                Log.w(TAG, "audioExtractor.release 실패", e)
            }

            // GL 리소스 정리 (EGL 컨텍스트가 살아있을 때)
            overlayRenderer.release()
            program.release()
            GLES30.glDeleteFramebuffers(1, fboId, 0)
            GLES30.glDeleteTextures(1, fboTexId, 0)
            GLES30.glDeleteTextures(1, intArrayOf(oesTexId), 0)

            // Surface / EGL 해제는 코덱 완전 종료 이후
            try {
                decoderOutputSurface.release()
            } catch (e: Exception) {
                Log.w(TAG, "decoderOutputSurface.release 실패", e)
            }
            try {
                decoderSurfaceTexture.release()
            } catch (e: Exception) {
                Log.w(TAG, "decoderSurfaceTexture.release 실패", e)
            }
            try {
                encoderSurface.release()
            } catch (e: Exception) {
                Log.w(TAG, "encoderSurface.release 실패", e)
            }
            try {
                encoderInputSurface.release()
            } catch (e: Exception) {
                Log.w(TAG, "encoderInputSurface.release 실패", e)
            }
            try {
                eglCore.release()
            } catch (e: Exception) {
                Log.w(TAG, "eglCore.release 실패", e)
            }
        }
    }.flowOn(transcodeDispatcher)

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        muxerStarted: Boolean,
        videoTrackIndex: Int,
        audioExtractor: MediaExtractor,
        audioTrackIndex: Int
    ): Triple<Boolean, Int, Int> {
        var started = muxerStarted
        var vIdx = videoTrackIndex
        var aIdx = -1

        while (true) {
            val encOutIdx = encoder.dequeueOutputBuffer(bufferInfo, 0)
            when {
                encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    vIdx = muxer.addTrack(encoder.outputFormat)
                    if (audioTrackIndex >= 0) {
                        aIdx = muxer.addTrack(audioExtractor.getTrackFormat(audioTrackIndex))
                    }
                    muxer.start()
                    started = true
                }
                encOutIdx >= 0 -> {
                    val encodedData = encoder.getOutputBuffer(encOutIdx) ?: break
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0 && started) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(vIdx, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(encOutIdx, false)
                }
                else -> break
            }
        }
        return Triple(started, vIdx, aIdx)
    }

    private fun drainEncoderFinal(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        muxerStarted: Boolean,
        videoTrackIndex: Int
    ) {
        if (!muxerStarted) return
        while (true) {
            val idx = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (idx < 0) break
            val data = encoder.getOutputBuffer(idx) ?: break
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                bufferInfo.size = 0
            }
            if (bufferInfo.size > 0) {
                data.position(bufferInfo.offset)
                data.limit(bufferInfo.offset + bufferInfo.size)
                muxer.writeSampleData(videoTrackIndex, data, bufferInfo)
            }
            val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
            encoder.releaseOutputBuffer(idx, false)
            if (isEos) break
        }
    }

    private fun muxAudioTrack(
        audioExtractor: MediaExtractor,
        muxer: MediaMuxer,
        muxerAudioTrackIndex: Int
    ) {
        val bufferSize = 1024 * 1024
        val buffer = ByteBuffer.allocate(bufferSize)
        val info = MediaCodec.BufferInfo()

        audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        while (true) {
            val sampleSize = audioExtractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            info.offset = 0
            info.size = sampleSize
            info.presentationTimeUs = audioExtractor.sampleTime
            info.flags = audioExtractor.sampleFlags
            muxer.writeSampleData(muxerAudioTrackIndex, buffer, info)
            audioExtractor.advance()
        }
    }

    private fun findTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(mimePrefix)) return i
        }
        return -1
    }

    private fun createOESTexture(): Int {
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texIds[0])
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return texIds[0]
    }

    private fun MediaFormat.getIntegerSafe(key: String, default: Int): Int {
        return try {
            getInteger(key)
        } catch (_: Exception) {
            default
        }
    }
}
