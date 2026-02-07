package com.kmu_focus.focusandroid.feature.detection.data.detector

import android.graphics.Bitmap
import android.util.Log
import com.kmu_focus.focusandroid.feature.detection.domain.detector.FaceDetector
import com.kmu_focus.focusandroid.feature.detection.domain.entity.DetectedFace
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceDetectorYN
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YuNetOpenCVDetector @Inject constructor(
    private val modelFileProvider: ModelFileProvider
) : FaceDetector {

    companion object {
        private const val TAG = "YuNetDetector"
        private const val MODEL_NAME = "yunet_face.onnx"
        private const val DEFAULT_INPUT_SIZE = 320
        private const val SCORE_THRESHOLD = 0.5f
        private const val NMS_THRESHOLD = 0.3f
        private const val TOP_K = 5000
    }

    private var detector: FaceDetectorYN? = null

    // GC 압박 방지를 위해 Mat 객체 재사용
    private var originalMat: Mat? = null
    private var bgrMat: Mat? = null
    private var smallMat: Mat? = null
    private var facesMat: Mat? = null

    private fun ensureInitialized() {
        if (detector != null) return

        val modelPath = modelFileProvider.getModelPath(MODEL_NAME)
        detector = FaceDetectorYN.create(
            modelPath,
            "",
            Size(DEFAULT_INPUT_SIZE.toDouble(), DEFAULT_INPUT_SIZE.toDouble()),
            SCORE_THRESHOLD,
            NMS_THRESHOLD,
            TOP_K
        ) ?: throw IllegalStateException("YuNet 검출기 초기화 실패")
    }

    override fun detectFaces(frame: Bitmap): List<DetectedFace> {
        ensureInitialized()
        val det = detector ?: return emptyList()

        return try {
            // Bitmap → Mat (RGBA)
            val orgMat = originalMat ?: Mat().also { originalMat = it }
            Utils.bitmapToMat(frame, orgMat)

            // RGBA/RGB → BGR
            val bgr = bgrMat ?: Mat().also { bgrMat = it }
            if (orgMat.type() == CvType.CV_8UC4 && orgMat.channels() == 4) {
                Imgproc.cvtColor(orgMat, bgr, Imgproc.COLOR_RGBA2BGR)
            } else {
                Imgproc.cvtColor(orgMat, bgr, Imgproc.COLOR_RGB2BGR)
            }

            // 비율 유지 리사이즈 — 가로를 inputSize로 고정, 세로는 비율에 맞춤
            val origWidth = bgr.cols()
            val origHeight = bgr.rows()
            val scale = DEFAULT_INPUT_SIZE.toFloat() / origWidth
            val smallWidth = DEFAULT_INPUT_SIZE
            val smallHeight = (origHeight * scale).toInt()

            val small = smallMat ?: Mat().also { smallMat = it }
            Imgproc.resize(bgr, small, Size(smallWidth.toDouble(), smallHeight.toDouble()))

            det.setInputSize(Size(small.cols().toDouble(), small.rows().toDouble()))

            // 검출
            val faces = facesMat ?: Mat().also { facesMat = it }
            det.detect(small, faces)

            // 파싱 — 스케일 복원은 1/scale (= origWidth/inputSize)
            if (faces.rows() > 0 && faces.cols() >= 15) {
                val rows = (0 until faces.rows()).map { i ->
                    FloatArray(15).also { data -> faces.row(i).get(0, 0, data) }
                }
                val scaleBack = 1f / scale
                parseFaceOutput(rows, scaleBack, scaleBack).map { face ->
                    // 좌표 클리핑 — 원본 프레임 범위 내로 제한 (테스트 프로젝트와 동일)
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
