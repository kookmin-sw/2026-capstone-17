package com.kmu_focus.focusandroid.feature.ai.data.yunet

import android.graphics.Bitmap
import android.util.Log
import com.kmu_focus.focusandroid.feature.ai.domain.config.DetectionConfig
import com.kmu_focus.focusandroid.feature.ai.domain.detector.FaceDetector
import com.kmu_focus.focusandroid.feature.ai.domain.entity.DetectedFace
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceDetectorYN
import javax.inject.Inject

class YuNetOpenCVDetector @Inject constructor(
    private val modelFileProvider: ModelFileProvider,
    private val config: DetectionConfig
) : FaceDetector {

    companion object {
        private const val TAG = "YuNetDetector"
    }

    private var detector: FaceDetectorYN? = null

    private var originalMat: Mat? = null
    private var bgrMat: Mat? = null
    private var smallMat: Mat? = null
    private var facesMat: Mat? = null

    private fun ensureInitialized() {
        if (detector != null) return

        val modelPath = modelFileProvider.getModelPath(config.modelName)
        detector = FaceDetectorYN.create(
            modelPath,
            "",
            Size(config.inputSize.toDouble(), config.inputSize.toDouble()),
            config.scoreThreshold,
            config.nmsThreshold,
            config.topK
        ) ?: throw IllegalStateException("YuNet 검출기 초기화 실패")
    }

    override fun detectFaces(frame: Bitmap): List<DetectedFace> {
        ensureInitialized()
        val det = detector ?: return emptyList()

        return try {
            val orgMat = originalMat ?: Mat().also { originalMat = it }
            Utils.bitmapToMat(frame, orgMat)

            val bgr = bgrMat ?: Mat().also { bgrMat = it }
            if (orgMat.type() == CvType.CV_8UC4 && orgMat.channels() == 4) {
                Imgproc.cvtColor(orgMat, bgr, Imgproc.COLOR_RGBA2BGR)
            } else {
                Imgproc.cvtColor(orgMat, bgr, Imgproc.COLOR_RGB2BGR)
            }

            val origWidth = bgr.cols()
            val origHeight = bgr.rows()
            val scale = config.inputSize.toFloat() / origWidth
            val smallWidth = config.inputSize
            val smallHeight = (origHeight * scale).toInt()

            val small = smallMat ?: Mat().also { smallMat = it }
            Imgproc.resize(bgr, small, Size(smallWidth.toDouble(), smallHeight.toDouble()))

            det.setInputSize(Size(small.cols().toDouble(), small.rows().toDouble()))

            val faces = facesMat ?: Mat().also { facesMat = it }
            det.detect(small, faces)

            if (faces.rows() > 0 && faces.cols() >= 15) {
                val rows = (0 until faces.rows()).map { i ->
                    FloatArray(15).also { data -> faces.row(i).get(0, 0, data) }
                }
                val scaleBack = 1f / scale
                parseFaceOutput(rows, scaleBack, scaleBack).map { face ->
                    val cx = face.x.coerceIn(0, frame.width - 1)
                    val cy = face.y.coerceIn(0, frame.height - 1)
                    face.copy(
                        x = cx,
                        y = cy,
                        width = face.width.coerceIn(1, frame.width - cx),
                        height = face.height.coerceIn(1, frame.height - cy)
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "얼굴 검출 실패", e)
            emptyList()
        }
    }

    override fun release() {
        detector = null
        originalMat?.release()
        bgrMat?.release()
        smallMat?.release()
        facesMat?.release()
        originalMat = null
        bgrMat = null
        smallMat = null
        facesMat = null
    }

    override fun getDetectorType(): String = "YuNet OpenCV (CPU)"
}
