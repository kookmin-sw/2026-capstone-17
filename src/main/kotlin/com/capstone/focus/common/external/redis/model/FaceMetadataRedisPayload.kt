package com.capstone.focus.common.external.redis.model

import com.fasterxml.jackson.annotation.JsonProperty

data class FaceMetadataRedisPayload(
    @JsonProperty("session_id")
    val sessionId: String,
    @JsonProperty("pts_us")
    val ptsUs: Long,
    @JsonProperty("faces")
    val faces: List<FrameFaceRedisPayload>
)

data class FrameFaceRedisPayload(
    @JsonProperty("tracking_id")
    val trackingId: Long,
    @JsonProperty("bbox")
    val boundingBox: BoundingBoxRedisPayload?,
    @JsonProperty("tdmm_raw")
    val tdmmRaw: TdmmRawRedisPayload?
)

data class BoundingBoxRedisPayload(
    @JsonProperty("x")
    val x: Int,
    @JsonProperty("y")
    val y: Int,
    @JsonProperty("width")
    val width: Int,
    @JsonProperty("height")
    val height: Int
)

data class TdmmRawRedisPayload(
    @JsonProperty("coeffs")
    val coefficients: List<Float>
)
