package com.capstone.focus.common.external.youtube.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class YoutubeTokenResponse(
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("expires_in")
    val expiresIn: Long,
    @JsonProperty("refresh_token")
    val refreshToken: String? = null,
    @JsonProperty("scope")
    val scope: String? = null,
    @JsonProperty("token_type")
    val tokenType: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YoutubeChannelsResponse(
    val items: List<YoutubeChannelItem>? = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YoutubeChannelItem(
    val id: String,
    val snippet: YoutubeChannelSnippet? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YoutubeChannelSnippet(
    val title: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YoutubeLiveBroadcastInsertRequest(
    val snippet: YoutubeLiveBroadcastSnippet,
    val status: YoutubeLiveBroadcastStatus,
    val contentDetails: YoutubeLiveBroadcastContentDetails
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YoutubeLiveBroadcastSnippet(
    val title: String,
    val scheduledStartTime: String,
    val description: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YoutubeLiveBroadcastStatus(
    val privacyStatus: String,
    val selfDeclaredMadeForKids: Boolean
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YoutubeLiveBroadcastContentDetails(
    val enableAutoStart: Boolean,
    val enableAutoStop: Boolean,
    val recordFromStart: Boolean,
    val enableDvr: Boolean = true,
    val latencyPreference: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YoutubeLiveBroadcastResponse(
    val id: String,
    val snippet: YoutubeLiveBroadcastSnippet? = null,
    val status: Map<String, Any?>? = null,
    val contentDetails: Map<String, Any?>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YoutubeLiveStreamInsertRequest(
    val snippet: YoutubeLiveStreamSnippet,
    val cdn: YoutubeLiveStreamCdn
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YoutubeLiveStreamSnippet(
    val title: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YoutubeLiveStreamCdn(
    val ingestionType: String = "rtmp",
    val frameRate: String = "variable",
    val resolution: String = "variable"
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YoutubeLiveStreamResponse(
    val id: String,
    val cdn: YoutubeLiveStreamCdnResponse? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YoutubeLiveStreamCdnResponse(
    val ingestionInfo: YoutubeIngestionInfo? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YoutubeIngestionInfo(
    val streamName: String,
    val ingestionAddress: String,
    val backupIngestionAddress: String? = null,
    val rtmpsIngestionAddress: String? = null
)
