package com.capstone.focus.common.external.chzzk.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class ChzzkApiResponse<T>(
    val code: Int,
    val message: String?,
    val content: T?
)

data class ChzzkTokenIssueRequest(
    @JsonProperty("grantType")
    val grantType: String,
    @JsonProperty("clientId")
    val clientId: String,
    @JsonProperty("clientSecret")
    val clientSecret: String,
    @JsonProperty("code")
    val code: String,
    @JsonProperty("state")
    val state: String
)

data class ChzzkTokenRefreshRequest(
    @JsonProperty("grantType")
    val grantType: String,
    @JsonProperty("refreshToken")
    val refreshToken: String,
    @JsonProperty("clientId")
    val clientId: String,
    @JsonProperty("clientSecret")
    val clientSecret: String
)

data class ChzzkTokenRevokeRequest(
    @JsonProperty("clientId")
    val clientId: String,
    @JsonProperty("clientSecret")
    val clientSecret: String,
    @JsonProperty("token")
    val token: String,
    @JsonProperty("tokenTypeHint")
    val tokenTypeHint: String = "access_token"
)

data class ChzzkTokenContent(
    @JsonProperty("accessToken")
    val accessToken: String,
    @JsonProperty("refreshToken")
    val refreshToken: String,
    @JsonProperty("tokenType")
    val tokenType: String,
    @JsonProperty("expiresIn")
    val expiresIn: String
)

data class ChzzkUserMeContent(
    @JsonProperty("channelId")
    val channelId: String,
    @JsonProperty("channelName")
    val channelName: String
)

data class ChzzkStreamKeyContent(
    @JsonProperty("streamKey")
    val streamKey: String
)

data class ChzzkLiveSettingPatchRequest(
    @JsonProperty("defaultLiveTitle")
    val defaultLiveTitle: String? = null,
    @JsonProperty("categoryType")
    val categoryType: String? = null,
    @JsonProperty("categoryId")
    val categoryId: String? = null,
    @JsonProperty("tags")
    val tags: List<String>? = null
)

data class ChzzkLiveListContent(
    @JsonProperty("data")
    val data: List<ChzzkLiveContent> = emptyList(),
    @JsonProperty("page")
    val page: ChzzkCursorPage? = null
)

data class ChzzkCursorPage(
    @JsonProperty("next")
    val next: String? = null
)

data class ChzzkLiveContent(
    @JsonProperty("liveId")
    val liveId: Long? = null,
    @JsonProperty("liveTitle")
    val liveTitle: String? = null,
    @JsonProperty("concurrentUserCount")
    val concurrentUserCount: Long? = null,
    @JsonProperty("openDate")
    val openDate: String? = null,
    @JsonProperty("categoryType")
    val categoryType: String? = null,
    @JsonProperty("liveCategory")
    val liveCategory: String? = null,
    @JsonProperty("liveCategoryValue")
    val liveCategoryValue: String? = null,
    @JsonProperty("channelId")
    val channelId: String,
    @JsonProperty("channelName")
    val channelName: String? = null
)
