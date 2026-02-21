package com.kmu_focus.focusandroid.core.metadata.domain.entity

data class FrameMetadata(
    val sessionId: String,
    val ptsUs: Long,
    val faces: List<FaceData>,
)

data class FaceData(
    val trackingId: Int,
    val bbox: BBox,
    val tdmm: ThreeDMM,
)

data class BBox(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

enum class ThreeDMMFormat {
    ID80_EXP64_POSE6_V1,
    FACEMAP_3DMM_265_V1,
    CUSTOM,
}

data class ThreeDMM(
    val format: ThreeDMMFormat,
    val modelVersion: String,
    val coeffs: FloatArray,
    val idDim: Int,
    val expDim: Int,
    val poseDim: Int,
    val extraDim: Int = 0,
) {
    override fun equals(other: Any?): Boolean =
        other is ThreeDMM &&
            format == other.format &&
            modelVersion == other.modelVersion &&
            idDim == other.idDim &&
            expDim == other.expDim &&
            poseDim == other.poseDim &&
            extraDim == other.extraDim &&
            coeffs.contentEquals(other.coeffs)

    override fun hashCode(): Int =
        format.hashCode() +
            31 * modelVersion.hashCode() +
            31 * 31 * coeffs.contentHashCode() +
            31 * 31 * 31 * idDim +
            31 * 31 * 31 * 31 * expDim +
            31 * 31 * 31 * 31 * 31 * poseDim +
            31 * 31 * 31 * 31 * 31 * 31 * extraDim
}
