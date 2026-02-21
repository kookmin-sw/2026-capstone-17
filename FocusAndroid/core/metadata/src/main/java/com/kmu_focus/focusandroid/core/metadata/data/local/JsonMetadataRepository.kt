package com.kmu_focus.focusandroid.core.metadata.data.local

import com.kmu_focus.focusandroid.core.metadata.di.MetadataOutputDir
import com.kmu_focus.focusandroid.core.metadata.domain.entity.FaceData
import com.kmu_focus.focusandroid.core.metadata.domain.entity.FrameMetadata
import com.kmu_focus.focusandroid.core.metadata.domain.repository.MetadataRepository
import java.io.File
import javax.inject.Inject

class JsonMetadataRepository @Inject constructor(
    @MetadataOutputDir private val outputDir: File,
    private val metadataDocumentWriter: MetadataDocumentWriter? = null,
) : MetadataRepository {

    private val lock = Any()
    private val frameBuffer = mutableListOf<FrameMetadata>()
    private var isClosed = false

    override suspend fun sendFrame(metadata: FrameMetadata) {
        synchronized(lock) {
            check(!isClosed) { "MetadataRepository is already closed" }
            frameBuffer += metadata.deepCopy()
        }
    }

    override suspend fun close() {
        val snapshot: List<FrameMetadata>
        synchronized(lock) {
            if (isClosed) return
            isClosed = true
            snapshot = frameBuffer.toList()
            frameBuffer.clear()
        }

        val json = buildJson(snapshot)
        val savedToDocuments = metadataDocumentWriter?.write(json) == true
        if (!savedToDocuments) {
            outputDir.mkdirs()
            val outputFile = File(outputDir, "metadata_${System.currentTimeMillis()}.json")
            outputFile.writeText(json)
        }
    }

    private fun buildJson(frames: List<FrameMetadata>): String {
        val builder = StringBuilder()
        builder.append("{\"frames\":[")
        frames.forEachIndexed { index, frame ->
            if (index > 0) builder.append(',')
            appendFrame(builder, frame)
        }
        builder.append("]}")
        return builder.toString()
    }

    private fun appendFrame(builder: StringBuilder, frame: FrameMetadata) {
        builder.append('{')
        builder.append("\"session_id\":\"").append(escapeJson(frame.sessionId)).append('\"')
        builder.append(',')
        builder.append("\"pts_us\":").append(frame.ptsUs)
        builder.append(',')
        builder.append("\"faces\":[")
        frame.faces.forEachIndexed { index, face ->
            if (index > 0) builder.append(',')
            appendFace(builder, face)
        }
        builder.append(']')
        builder.append('}')
    }

    private fun appendFace(builder: StringBuilder, face: FaceData) {
        builder.append('{')
        builder.append("\"tracking_id\":").append(face.trackingId)
        builder.append(',')
        builder.append("\"bbox\":{")
        builder.append("\"x\":").append(face.bbox.x).append(',')
        builder.append("\"y\":").append(face.bbox.y).append(',')
        builder.append("\"width\":").append(face.bbox.width).append(',')
        builder.append("\"height\":").append(face.bbox.height)
        builder.append('}')
        builder.append(',')
        builder.append("\"tdmm_raw\":{")
        builder.append("\"format\":\"").append(face.tdmm.format.name).append('\"')
        builder.append(',')
        builder.append("\"model_version\":\"").append(escapeJson(face.tdmm.modelVersion)).append('\"')
        builder.append(',')
        builder.append("\"id_dim\":").append(face.tdmm.idDim)
        builder.append(',')
        builder.append("\"exp_dim\":").append(face.tdmm.expDim)
        builder.append(',')
        builder.append("\"pose_dim\":").append(face.tdmm.poseDim)
        builder.append(',')
        builder.append("\"extra_dim\":").append(face.tdmm.extraDim)
        builder.append(',')
        builder.append("\"coeffs\":")
        appendFloatArray(builder, face.tdmm.coeffs)
        builder.append('}')
        builder.append('}')
    }

    private fun appendFloatArray(builder: StringBuilder, values: FloatArray) {
        builder.append('[')
        values.forEachIndexed { index, value ->
            if (index > 0) builder.append(',')
            builder.append(value)
        }
        builder.append(']')
    }

    private fun escapeJson(input: String): String = input
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

    private fun FrameMetadata.deepCopy(): FrameMetadata = copy(
        faces = faces.map { face ->
            face.copy(
                tdmm = face.tdmm.copy(
                    coeffs = face.tdmm.coeffs.copyOf(),
                )
            )
        }
    )
}
