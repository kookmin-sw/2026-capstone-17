package com.capstone.focus.common.external.fastapi.dto

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty

data class FastApiStartStreamRequest(
    @JsonProperty("broadcast_id")
    val broadcastId: String,
    @JsonProperty("input_stream_key")
    val inputStreamKey: String,
    @JsonProperty("stream_key")
    val streamKey: String = inputStreamKey,
    @JsonProperty("avatar_id")
    val avatarId: String? = null,
    @JsonProperty("output_mode")
    val outputMode: String,
    @JsonProperty("output_url")
    val outputUrl: String? = null,
    @JsonProperty("watch_url")
    val watchUrl: String? = null
)

data class FastApiStopStreamRequest(
    @JsonProperty("broadcast_id")
    val broadcastId: String
)

data class FastApiStreamResponse(
    @JsonProperty("broadcast_id")
    val broadcastId: String,
    @JsonProperty("input_stream_key")
    @JsonAlias("stream_key")
    val inputStreamKey: String,
    @JsonProperty("stream_key")
    val streamKey: String = inputStreamKey,
    @JsonProperty("state")
    val state: String,
    @JsonProperty("processed_frames")
    val processedFrames: Int,
    @JsonProperty("dropped_frames")
    val droppedFrames: Int,
    @JsonProperty("last_pts_us")
    val lastPtsUs: Long?,
    @JsonProperty("output_mode")
    val outputMode: String,
    @JsonProperty("input_url")
    val inputUrl: String,
    @JsonProperty("output_url")
    @JsonAlias("output_path")
    val outputUrl: String,
    @JsonProperty("watch_url")
    @JsonAlias("hls_url")
    val watchUrl: String?,
    @JsonProperty("detail")
    val detail: String?
)
