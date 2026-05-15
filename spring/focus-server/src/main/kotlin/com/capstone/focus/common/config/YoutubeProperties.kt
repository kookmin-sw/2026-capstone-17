package com.capstone.focus.common.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "google.youtube")
data class YoutubeProperties(
    var clientId: String = "",
    var clientSecret: String = "",
    var redirectUri: String = "",
    var authorizationUri: String = "https://accounts.google.com/o/oauth2/v2/auth",
    var tokenUri: String = "https://oauth2.googleapis.com/token",
    var revokeUri: String = "https://oauth2.googleapis.com/revoke",
    var apiBaseUrl: String = "https://www.googleapis.com",
    var scope: String = "https://www.googleapis.com/auth/youtube.force-ssl",
    var watchUrlTemplate: String = "https://www.youtube.com/watch?v={broadcastId}",
    var privacyStatus: String = "public",
    var latencyPreference: String = "low",
    var enableAutoStart: Boolean = true,
    var enableAutoStop: Boolean = true,
    var recordFromStart: Boolean = true,
    var madeForKids: Boolean = false,
    var preferRtmps: Boolean = false,
    var oauthStateTtlSeconds: Long = 600,
    var tokenRefreshBufferSeconds: Long = 60
)
