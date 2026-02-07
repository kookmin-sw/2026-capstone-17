package com.kmu_focus.focusandroid.feature.ai.data.model3dmm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import com.kmu_focus.focusandroid.feature.ai.domain.detector.Facial3DMMExtractor
import com.kmu_focus.focusandroid.feature.ai.domain.entity.Face3DMMCoeffs
import com.kmu_focus.focusandroid.feature.ai.domain.entity.Face3DMMResult
import com.kmu_focus.focusandroid.feature.ai.domain.entity.FaceRect
import com.kmu_focus.focusandroid.feature.ai.domain.entity.Vertex2D
import com.kmu_focus.focusandroid.feature.ai.domain.entity.Vertex3D
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject

private const val TAG = "Facial3DMMDetector"
private const val MODEL_NAME = "facial_3DMM.tflite"
private const val INPUT_SIZE = 128

class TFLiteFacial3DMMDetector @Inject constructor(
    @ApplicationContext private val context: Context
) : Facial3DMMExtractor {

    companion object {
        @JvmStatic
        var idDim: Int = 80
        @JvmStatic
        var expDim: Int = 64
        @JvmStatic
        var enableBenchmark: Boolean = true
    }

    private var interpreter: Interpreter? = null
    private var nnApiDelegate: NnApiDelegate? = null
    private var gpuDelegate: GpuDelegate? = null
    private var inputBuffer: ByteBuffer? = null

    private var numVertices: Int = 0
    private var numChannels: Int = 2
    private var outputShape: IntArray = intArrayOf()
    private var isQuantized: Boolean = false
    private var isCoefficientMode: Boolean = false
    private var coefficientSize: Int = 0

    private var frameCounter: Int = 0
    private var totalInferenceMs: Long = 0

    init {
        initialize()
    }

    private fun initialize() {
        try {
            val modelBuffer = loadModelFile(MODEL_NAME)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                try {
                    nnApiDelegate = NnApiDelegate(
                        NnApiDelegate.Options()
                            .setExecutionPreference(NnApiDelegate.Options.EXECUTION_PREFERENCE_FAST_SINGLE_ANSWER)
                            .setAllowFp16(true)
                    )
                    addDelegate(nnApiDelegate!!)
                    Log.i(TAG, "NNAPI Delegate 활성화")
                } catch (e: Exception) {
                    Log.w(TAG, "NNAPI 실패, GPU 시도: ${e.message}")
                    try {
                        if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                            gpuDelegate = GpuDelegate()
                            addDelegate(gpuDelegate!!)
                            Log.i(TAG, "GPU Delegate 활성화")
                        }
                    } catch (e2: Exception) {
                        Log.w(TAG, "GPU 실패, CPU 사용: ${e2.message}")
                    }
                }
            }
            interpreter = Interpreter(modelBuffer, options)

            val inputTensor = interpreter!!.getInputTensor(0)
            Log.i(TAG, "입력 shape: ${inputTensor.shape().contentToString()}")

            val outputTensor = interpreter!!.getOutputTensor(0)
            outputShape = outputTensor.shape()

            if (outputShape.size == 2 && outputShape[0] == 1) {
                coefficientSize = outputShape[1]
                isCoefficientMode = true
                numVertices = 0
                numChannels = 0
                Log.i(TAG, "✓ 3DMM 계수 모드: 출력 [1, $coefficientSize]")
            } else {
                if (outputShape.size == 3) {
                    numVertices = outputShape[1]
                    numChannels = outputShape[2].coerceIn(2, 3)
                } else {
                    numChannels = 2
                    numVertices = outputShape[1] / 2
                    if (outputShape[1] % 3 == 0) {
                        numChannels = 3
                        numVertices = outputShape[1] / 3
                    }
                }
                Log.i(TAG, "✓ 정점 모드: ${numVertices}개, ${numChannels}D")
            }

            isQuantized = inputTensor.dataType() == DataType.UINT8
            val bytesPerChannel = if (isQuantized) 1 else 4
            inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * bytesPerChannel)
                .order(ByteOrder.nativeOrder())
        } catch (e: Exception) {
            Log.w(TAG, "3DMM 모델 로드 실패($MODEL_NAME), 3DMM 비활성: ${e.message}")
            interpreter = null
        }
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    override fun extract3DMM(frame: Bitmap, faceRect: Rect): Face3DMMResult? {
        val interp = interpreter ?: return null
        try {
            val t0 = SystemClock.elapsedRealtimeNanos()
            val safeFaceRect = Rect(
                faceRect.left.coerceIn(0, frame.width - 1),
                faceRect.top.coerceIn(0, frame.height - 1),
                faceRect.right.coerceIn(1, frame.width),
                faceRect.bottom.coerceIn(1, frame.height)
            )
            if (safeFaceRect.width() <= 0 || safeFaceRect.height() <= 0) return null

            val faceCrop = Bitmap.createBitmap(
                frame,
                safeFaceRect.left,
                safeFaceRect.top,
                safeFaceRect.width(),
                safeFaceRect.height()
            )
            val resized = Bitmap.createScaledBitmap(faceCrop, INPUT_SIZE, INPUT_SIZE, true)
            if (faceCrop != resized) faceCrop.recycle()
            prepareInputBuffer(resized)
            resized.recycle()

            val faceRectDomain = FaceRect(
                safeFaceRect.left,
                safeFaceRect.top,
                safeFaceRect.right,
                safeFaceRect.bottom
            )

            if (isCoefficientMode) {
                val outTensor = interp.getOutputTensor(0)
                val size = coefficientSize
                val coeffs = if (outTensor.dataType() == DataType.UINT8) {
                    val outBuf = Array(1) { ByteArray(size) }
                    interp.run(inputBuffer, outBuf)
                    val scale = outTensor.quantizationParams().scale
                    val zeroPoint = outTensor.quantizationParams().zeroPoint
                    FloatArray(size) { i -> ((outBuf[0][i].toInt() and 0xFF) - zeroPoint) * scale }
                } else {
                    val outBuf = Array(1) { FloatArray(size) }
                    interp.run(inputBuffer, outBuf)
                    outBuf[0].copyOf()
                }
                val poseDim = size - idDim - expDim
                val idCoeffs = if (idDim > 0 && size >= idDim) coeffs.copyOfRange(0, idDim) else floatArrayOf()
                val expCoeffs = if (expDim > 0 && size >= idDim + expDim) coeffs.copyOfRange(idDim, idDim + expDim) else floatArrayOf()
                val pose = if (poseDim > 0 && size >= idDim + expDim + poseDim) coeffs.copyOfRange(idDim + expDim, size) else floatArrayOf()
                frameCounter++
                if (enableBenchmark && frameCounter % 30 == 0) {
                    val t1 = SystemClock.elapsedRealtimeNanos()
                    Log.d(TAG, "[3DMM 계수] 평균 추론: ${(t1 - t0) / 1_000_000}ms")
                }
                return Face3DMMResult(
                    vertices = emptyList(),
                    faceRect = faceRectDomain,
                    coeffs = Face3DMMCoeffs(idCoeffs, expCoeffs, pose)
                )
            }

            val outputSize = outputShape[1] * if (outputShape.size == 3) outputShape[2] else 1
            val vertices: List<Vertex2D>
            val vertices3D: List<Vertex3D>?

            if (isQuantized) {
                val outputBuffer = Array(1) { ByteArray(outputSize) }
                interp.run(inputBuffer, outputBuffer)
                val parsed = parseOutputQuantized(outputBuffer[0])
                vertices = parsed.first
                vertices3D = parsed.second
            } else {
                val outputBuffer = Array(1) { FloatArray(outputSize) }
                interp.run(inputBuffer, outputBuffer)
                val parsed = parseOutput(outputBuffer[0])
                vertices = parsed.first
                vertices3D = parsed.second
            }

            val t1 = SystemClock.elapsedRealtimeNanos()
            frameCounter++
            if (enableBenchmark) {
                totalInferenceMs += (t1 - t0) / 1_000_000
                if (frameCounter % 30 == 0) {
                    Log.d(TAG, "[3DMM 정점] 평균 추론: ${totalInferenceMs / frameCounter}ms")
                }
            }

            val absolute3D = vertices3D?.let { toAbsolute3D(faceRectDomain, it) }
            return Face3DMMResult(
                vertices = vertices,
                faceRect = faceRectDomain,
                vertices3D = absolute3D,
                rawVertices3D = vertices3D
            )
        } catch (e: Exception) {
            Log.e(TAG, "3DMM 추출 오류: ${e.message}", e)
            return null
        }
    }

    private fun prepareInputBuffer(bitmap: Bitmap) {
        val buffer = inputBuffer ?: return
        buffer.rewind()
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        if (isQuantized) {
            for (pixel in pixels) {
                buffer.put(((pixel shr 16) and 0xFF).toByte())
                buffer.put(((pixel shr 8) and 0xFF).toByte())
                buffer.put((pixel and 0xFF).toByte())
            }
        } else {
            for (pixel in pixels) {
                buffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
                buffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
                buffer.putFloat((pixel and 0xFF) / 255.0f)
            }
        }
    }

    private fun parseOutput(output: FloatArray): Pair<List<Vertex2D>, List<Vertex3D>?> {
        val list = mutableListOf<Vertex2D>()
        val list3D = if (numChannels == 3) mutableListOf<Vertex3D>() else null
        val stride = numChannels
        for (i in 0 until numVertices) {
            val xNorm = output[i * stride].coerceIn(0f, 1f)
            val yNorm = output[i * stride + 1].coerceIn(0f, 1f)
            list.add(Vertex2D(xNorm, yNorm))
            if (numChannels == 3) {
                list3D!!.add(
                    Vertex3D(
                        i,
                        output[i * stride],
                        output[i * stride + 1],
                        output[i * stride + 2]
                    )
                )
            }
        }
        return list to list3D
    }

    private fun parseOutputQuantized(output: ByteArray): Pair<List<Vertex2D>, List<Vertex3D>?> {
        val list = mutableListOf<Vertex2D>()
        val list3D = if (numChannels == 3) mutableListOf<Vertex3D>() else null
        val stride = numChannels
        val outTensor = interpreter?.getOutputTensor(0)
        val scale = outTensor?.quantizationParams()?.scale ?: 0.0563f
        val zeroPoint = outTensor?.quantizationParams()?.zeroPoint ?: 132
        for (i in 0 until numVertices) {
            val rawX = output[i * stride].toInt() and 0xFF
            val rawY = output[i * stride + 1].toInt() and 0xFF
            val dequantX = (rawX - zeroPoint) * scale
            val dequantY = (rawY - zeroPoint) * scale
            list.add(Vertex2D(dequantX.coerceIn(0f, 1f), dequantY.coerceIn(0f, 1f)))
            if (numChannels == 3) {
                val zRaw = (output[i * stride + 2].toInt() and 0xFF - zeroPoint) * scale
                list3D!!.add(Vertex3D(i, dequantX, dequantY, zRaw))
            }
        }
        return list to list3D
    }

    private fun toAbsolute3D(faceRect: FaceRect, normalized: List<Vertex3D>): List<Vertex3D> =
        normalized.map { v ->
            Vertex3D(
                v.index,
                faceRect.left + v.x * faceRect.width(),
                faceRect.top + v.y * faceRect.height(),
                v.z
            )
        }

    override fun release() {
        interpreter?.close()
        interpreter = null
        nnApiDelegate?.close()
        nnApiDelegate = null
        gpuDelegate?.close()
        gpuDelegate = null
    }
}
