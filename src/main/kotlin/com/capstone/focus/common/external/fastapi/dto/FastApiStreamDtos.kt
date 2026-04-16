package com.capstone.focus.common.external.fastapi.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class FastApiStartStreamRequest(
    @JsonProperty("broadcast_id")
    val broadcastId: String,
    @JsonProperty("stream_key")
    val streamKey: String,
    @JsonProperty("avatar_id")
    val avatarId: String? = null
)

data class FastApiStopStreamRequest(
    @JsonProperty("broadcast_id")
    val broadcastId: String
)

data class FastApiStreamResponse(
    @JsonProperty("broadcast_id")
    val broadcastId: String,
    @JsonProperty("stream_key")
    val streamKey: String,
    @JsonProperty("state")
    val state: String,
    @JsonProperty("processed_frames")
    val processedFrames: Int,
    @JsonProperty("dropped_frames")
    val droppedFrames: Int,
    @JsonProperty("last_pts_us")
    val lastPtsUs: Long?,
    @JsonProperty("input_url")
    val inputUrl: String,
    @JsonProperty("output_path")
    val outputPath: String,
    @JsonProperty("hls_url")
    val hlsUrl: String,
    @JsonProperty("detail")
    val detail: String?
)
