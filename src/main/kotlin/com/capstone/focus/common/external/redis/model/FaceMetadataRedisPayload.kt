package com.capstone.focus.common.external.redis.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

data class FaceMetadataRedisPayload(
    @JsonProperty("session_id")
    val sessionId: String,
    @JsonProperty("pts_us")
    val ptsUs: Long,
    @JsonProperty("avatar_url")
    val avatarUrl: String,
    @JsonProperty("face_data")
    val faceData: Map<String, Float>,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("tracking_id")
    val trackingId: String?,
    @JsonProperty("is_reentry")
    val isReentry: Boolean,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("confidence")
    val confidence: Float?,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("bbox")
    val boundingBox: List<Float>,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("landmarks")
    val landmarks: List<FaceMetadataLandmark>
)

data class FaceMetadataLandmark(
    val x: Float,
    val y: Float,
    val z: Float
)
