package com.kmu_focus.focusandroid.feature.ai.domain.entity

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

data class Vertex2D(val x: Float, val y: Float)

data class Vertex3D(
    val index: Int,
    val x: Float,
    val y: Float,
    val z: Float
)

data class FaceRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun width(): Int = (right - left).coerceAtLeast(0)
    fun height(): Int = (bottom - top).coerceAtLeast(0)
}

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
