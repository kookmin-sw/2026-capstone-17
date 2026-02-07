package com.kmu_focus.focusandroid.feature.detection.domain.entity

/**
 * 3DMM 계수 (id/exp/pose) — 모델 출력 [1,K]를 id_coeffs, exp_coeffs, pose로 분할
 */
data class Face3DMMCoeffs(
    val idCoeffs: FloatArray,
    val expCoeffs: FloatArray,
    val pose: FloatArray
) {
    override fun equals(other: Any?) = (other is Face3DMMCoeffs) &&
        idCoeffs.contentEquals(other.idCoeffs) &&
        expCoeffs.contentEquals(other.expCoeffs) &&
        pose.contentEquals(other.pose)

    override fun hashCode() =
        idCoeffs.contentHashCode() + 31 * expCoeffs.contentHashCode() + 31 * 31 * pose.contentHashCode()
}

/** 3DMM 메시 정점 (2D, 정규화 0–1) — YuNet 5점 랜드마크와 구분 */
data class Vertex2D(val x: Float, val y: Float)

/** 3DMM 메시 정점 (3D, index 포함, 서버 전송용) */
data class Vertex3D(
    val index: Int,
    val x: Float,
    val y: Float,
    val z: Float
)

/** 얼굴 영역 (순수 Kotlin, domain) */
data class FaceRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun width(): Int = (right - left).coerceAtLeast(0)
    fun height(): Int = (bottom - top).coerceAtLeast(0)
}

/** 3DMM 모델 추출 결과 — 계수(id/exp/pose) 필수, 정점은 모델 출력 형태에 따라 선택 */
data class Face3DMMResult(
    val vertices: List<Vertex2D>,
    val faceRect: FaceRect,
    val vertices3D: List<Vertex3D>? = null,
    val rawVertices3D: List<Vertex3D>? = null,
    val coeffs: Face3DMMCoeffs? = null
) {
    fun getAbsoluteVertices(): List<Vertex2D> = vertices.map { v ->
        Vertex2D(
            x = faceRect.left + v.x * faceRect.width(),
            y = faceRect.top + v.y * faceRect.height()
        )
    }

    fun getVertices3DForExport(): List<Vertex3D> {
        if (!rawVertices3D.isNullOrEmpty()) return rawVertices3D
        if (!vertices3D.isNullOrEmpty()) return vertices3D
        return vertices.mapIndexed { i, v ->
            Vertex3D(
                index = i,
                x = faceRect.left + v.x * faceRect.width(),
                y = faceRect.top + v.y * faceRect.height(),
                z = 0f
            )
        }
    }

    val vertexCount: Int get() = vertices.size
}
