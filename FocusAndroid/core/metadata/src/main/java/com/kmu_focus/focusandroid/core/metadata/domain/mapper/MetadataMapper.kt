package com.kmu_focus.focusandroid.core.metadata.domain.mapper

import com.kmu_focus.focusandroid.core.metadata.domain.entity.BBox
import com.kmu_focus.focusandroid.core.metadata.domain.entity.FaceData
import com.kmu_focus.focusandroid.core.metadata.domain.entity.FrameMetadata
import com.kmu_focus.focusandroid.core.metadata.domain.entity.ThreeDMM
import com.kmu_focus.focusandroid.core.metadata.domain.entity.ThreeDMMFormat

object MetadataMapper {

    data class FaceExportPayload(
        val trackingId: Int,
        val bbox: IntArray,
        val idCoeffs: FloatArray?,
        val expCoeffs: FloatArray?,
        val pose: FloatArray?,
        val extraCoeffs: FloatArray? = null,
        val isOwner: Boolean?,
        val modelVersion: String? = null,
    )

    fun mapFrame(
        sessionId: String,
        timestampSeconds: Double,
        faces: List<FaceExportPayload>,
    ): FrameMetadata {
        val ptsUs = if (timestampSeconds.isFinite()) {
            (timestampSeconds * MICROS_PER_SECOND).toLong()
        } else {
            0L
        }

        val mappedFaces = faces.asSequence()
            .filter { it.isOwner == false }
            .mapNotNull { face ->
                val id = face.idCoeffs
                val exp = face.expCoeffs
                val pose = face.pose
                if (id == null || exp == null || pose == null) return@mapNotNull null
                if (face.bbox.size < BBOX_SIZE) return@mapNotNull null
                val extra = face.extraCoeffs ?: floatArrayOf()
                val idDim = id.size
                val expDim = exp.size
                val poseDim = pose.size
                val extraDim = extra.size

                FaceData(
                    trackingId = face.trackingId,
                    bbox = BBox(
                        x = face.bbox[0],
                        y = face.bbox[1],
                        width = face.bbox[2],
                        height = face.bbox[3],
                    ),
                    tdmm = ThreeDMM(
                        format = resolveFormat(idDim, expDim, poseDim, extraDim),
                        modelVersion = resolveModelVersion(face.modelVersion, idDim, expDim, poseDim, extraDim),
                        coeffs = concatCoeffs(id, exp, pose, extra),
                        idDim = idDim,
                        expDim = expDim,
                        poseDim = poseDim,
                        extraDim = extraDim,
                    ),
                )
            }
            .toList()

        return FrameMetadata(
            sessionId = sessionId,
            ptsUs = ptsUs,
            faces = mappedFaces,
        )
    }

    private const val MICROS_PER_SECOND = 1_000_000.0
    private const val BBOX_SIZE = 4
    private const val ID_COEFFS_DIM = 80
    private const val EXP_COEFFS_DIM = 64
    private const val POSE_DIM = 6
    private const val FACEMAP_ID_DIM = 219
    private const val FACEMAP_EXP_DIM = 39
    private const val FACEMAP_POSE_DIM = 6
    private const val FACEMAP_EXTRA_DIM = 1
    private const val DEFAULT_MODEL_VERSION = "unknown"
    private const val FACEMAP_MODEL_VERSION = "facial_3DMM.tflite"

    private fun concatCoeffs(
        idCoeffs: FloatArray,
        expCoeffs: FloatArray,
        pose: FloatArray,
        extraCoeffs: FloatArray,
    ): FloatArray {
        val out = FloatArray(idCoeffs.size + expCoeffs.size + pose.size + extraCoeffs.size)
        var offset = 0
        idCoeffs.copyInto(out, destinationOffset = offset)
        offset += idCoeffs.size
        expCoeffs.copyInto(out, destinationOffset = offset)
        offset += expCoeffs.size
        pose.copyInto(out, destinationOffset = offset)
        offset += pose.size
        extraCoeffs.copyInto(out, destinationOffset = offset)
        return out
    }

    private fun resolveFormat(idDim: Int, expDim: Int, poseDim: Int, extraDim: Int): ThreeDMMFormat {
        return when {
            idDim == ID_COEFFS_DIM && expDim == EXP_COEFFS_DIM && poseDim == POSE_DIM && extraDim == 0 ->
                ThreeDMMFormat.ID80_EXP64_POSE6_V1
            idDim == FACEMAP_ID_DIM && expDim == FACEMAP_EXP_DIM && poseDim == FACEMAP_POSE_DIM && extraDim == FACEMAP_EXTRA_DIM ->
                ThreeDMMFormat.FACEMAP_3DMM_265_V1
            else -> ThreeDMMFormat.CUSTOM
        }
    }

    private fun resolveModelVersion(
        provided: String?,
        idDim: Int,
        expDim: Int,
        poseDim: Int,
        extraDim: Int,
    ): String {
        if (!provided.isNullOrBlank()) return provided
        return if (
            idDim == FACEMAP_ID_DIM &&
            expDim == FACEMAP_EXP_DIM &&
            poseDim == FACEMAP_POSE_DIM &&
            extraDim == FACEMAP_EXTRA_DIM
        ) {
            FACEMAP_MODEL_VERSION
        } else {
            DEFAULT_MODEL_VERSION
        }
    }
}
