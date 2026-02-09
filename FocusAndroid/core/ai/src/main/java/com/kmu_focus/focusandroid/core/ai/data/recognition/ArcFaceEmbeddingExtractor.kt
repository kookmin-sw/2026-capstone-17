package com.kmu_focus.focusandroid.core.ai.data.recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.sqrt
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ArcFace 기반 얼굴 임베딩 추출 (w600k_mbf.onnx).
 * 입력: 112x112 RGB, (pixel - 127.5) / 128.0, NCHW [1, 3, 112, 112].
 */
@Singleton
class ArcFaceEmbeddingExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "ArcFaceEmbedding"
        private const val MODEL_NAME = "w600k_mbf.onnx"
        private const val INPUT_SIZE = 112
        private const val NORM_MEAN = 127.5f
        private const val NORM_STD = 128.0f
    }

    private val ortEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var inputName: String = ""
    private var outputName: String = ""
    val embeddingDim: Int
        get() = _embeddingDim

    private var _embeddingDim: Int = 512

    init {
        try {
            val modelBytes = context.assets.open(MODEL_NAME).use { it.readBytes() }
            val sessionOptions = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            session = ortEnvironment.createSession(modelBytes, sessionOptions)
            val s = session!!
            val inNames = s.inputNames
            val outNames = s.outputNames
            inputName = (inNames?.iterator()?.next() ?: inNames?.firstOrNull()) ?: ""
            outputName = (outNames?.iterator()?.next() ?: outNames?.firstOrNull()) ?: ""
            try {
                val outInfo = s.outputInfo[outputName]?.info
                if (outInfo is ai.onnxruntime.TensorInfo) {
                    val shape = outInfo.shape
                    if (shape != null && shape.isNotEmpty()) {
                        var d = 1
                        for (i in shape.indices) d *= shape[i].toInt()
                        _embeddingDim = d
                    }
                }
            } catch (_: Exception) {}
            Log.i(TAG, "w600k_mbf 로드: 입력=$inputName, 출력=$outputName, dim=$_embeddingDim")
        } catch (e: Exception) {
            Log.w(TAG, "w600k_mbf 미로드 (assets에 파일 없음?): ${e.message}")
        }
    }

    private fun preprocess(faceBitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        if (resized != faceBitmap) resized.recycle()

        val input = FloatArray(1 * 3 * INPUT_SIZE * INPUT_SIZE)
        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val pixel = pixels[y * INPUT_SIZE + x]
                val r = (Color.red(pixel) - NORM_MEAN) / NORM_STD
                val g = (Color.green(pixel) - NORM_MEAN) / NORM_STD
                val b = (Color.blue(pixel) - NORM_MEAN) / NORM_STD
                input[0 * (INPUT_SIZE * INPUT_SIZE) + y * INPUT_SIZE + x] = r
                input[1 * (INPUT_SIZE * INPUT_SIZE) + y * INPUT_SIZE + x] = g
                input[2 * (INPUT_SIZE * INPUT_SIZE) + y * INPUT_SIZE + x] = b
            }
        }
        return input
    }

    private fun l2Normalize(vec: FloatArray): FloatArray {
        var norm = 0.0
        for (v in vec) norm += v * v
        norm = sqrt(norm)
        if (norm < 1e-8) return vec
        return FloatArray(vec.size) { vec[it] / norm.toFloat() }
    }

    fun extractEmbedding(faceBitmap: Bitmap): FloatArray? {
        val s = session ?: return null
        if (faceBitmap.width < 16 || faceBitmap.height < 16) return null
        return try {
            val inputArr = preprocess(faceBitmap)
            val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
            val inputTensor = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(inputArr), shape)
            try {
                val result = s.run(mapOf(inputName to inputTensor))
                try {
                    val output = result.get(0) as OnnxTensor
                    val outputBuffer = output.floatBuffer
                    val dim = outputBuffer.remaining()
                    val embedding = FloatArray(dim)
                    outputBuffer.get(embedding)
                    l2Normalize(embedding)
                } finally {
                    result.close()
                }
            } finally {
                inputTensor.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "임베딩 추출 실패: ${e.message}")
            null
        }
    }

    fun release() {
        try {
            session?.close()
        } catch (_: Exception) {}
        session = null
    }
}
