package com.kmu_focus.focusandroid.feature.video.domain.entity

import org.json.JSONArray
import org.json.JSONObject

data class VideoInfo(
    val width: Int,
    val height: Int,
    val fps: Float
)

data class FaceExport(
    val trackingId: Int,
    val bbox: IntArray,
    val idCoeffs: FloatArray? = null,
    val expCoeffs: FloatArray? = null,
    val pose: FloatArray? = null,
    /** null = PENDING, true = OWNER(전송 제외), false = OTHER */
    val isOwner: Boolean? = null
) {
    override fun equals(other: Any?) = (other is FaceExport) &&
        trackingId == other.trackingId &&
        bbox.contentEquals(other.bbox) &&
        (idCoeffs == null && other.idCoeffs == null || idCoeffs != null && other.idCoeffs != null && idCoeffs.contentEquals(other.idCoeffs)) &&
        (expCoeffs == null && other.expCoeffs == null || expCoeffs != null && other.expCoeffs != null && expCoeffs.contentEquals(other.expCoeffs)) &&
        (pose == null && other.pose == null || pose != null && other.pose != null && pose.contentEquals(other.pose)) &&
        isOwner == other.isOwner

    override fun hashCode() = 31 * trackingId + bbox.contentHashCode() +
        (idCoeffs?.contentHashCode() ?: 0) + 31 * (expCoeffs?.contentHashCode() ?: 0) + 31 * 31 * (pose?.contentHashCode() ?: 0) + 31 * 31 * 31 * (isOwner?.hashCode() ?: 0)
}

data class FrameExport(
    val frameNumber: Int,
    val timestamp: Double,
    val faces: List<FaceExport>
) {
    /** isOwner == false(OTHER)만 포함 (Owner/PENDING 전송 금지). */
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("frame_number", frameNumber)
        put("timestamp", timestamp)
        put("faces", JSONArray().apply {
            faces.filter { it.isOwner == false }.forEach { face ->
                put(JSONObject().apply {
                    put("tracking_id", face.trackingId)
                    put("bbox", JSONArray().apply { face.bbox.forEach { put(it) } })
                    put("3dmm", JSONObject().apply {
                        put("id_coeffs", JSONArray().apply { (face.idCoeffs ?: floatArrayOf()).forEach { put(it.toDouble()) } })
                        put("exp_coeffs", JSONArray().apply { (face.expCoeffs ?: floatArrayOf()).forEach { put(it.toDouble()) } })
                        put("pose", JSONArray().apply { (face.pose ?: floatArrayOf()).forEach { put(it.toDouble()) } })
                    })
                })
            }
        })
    }
}

data class VideoExportData(
    val videoInfo: VideoInfo,
    val frames: List<FrameExport>
) {
    fun toJsonString(): String = toJsonObject().toString(2)

    fun toJsonObject(): JSONObject {
        val jo = JSONObject()
        jo.put("video_info", JSONObject().apply {
            put("width", videoInfo.width)
            put("height", videoInfo.height)
            put("fps", videoInfo.fps.toDouble())
            put("format", "3dmm")
        })
        jo.put("frames", JSONArray().apply {
            frames.forEach { put(it.toJsonObject()) }
        })
        return jo
    }
}

object VideoExportStreaming {
    fun writeHeader(writer: java.io.Writer, videoInfo: VideoInfo) {
        writer.write("{\"video_info\":{\"width\":${videoInfo.width},\"height\":${videoInfo.height},\"fps\":${videoInfo.fps},\"format\":\"3dmm\"},\"frames\":[")
    }

    fun writeFrame(writer: java.io.Writer, frame: FrameExport, isFirst: Boolean) {
        if (!isFirst) writer.write(",")
        writer.write(frame.toJsonObject().toString())
    }

    fun writeFooter(writer: java.io.Writer) {
        writer.write("]}")
    }
}
