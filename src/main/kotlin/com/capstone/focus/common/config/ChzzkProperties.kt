package com.capstone.focus.common.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "naver.chzzk")
data class ChzzkProperties(
    var clientId: String = "",
    var clientSecret: String = "",
    var redirectUri: String = "",
    var authorizationUri: String = "https://chzzk.naver.com/account-interlock",
    var openApiBaseUrl: String = "https://openapi.chzzk.naver.com",
    var watchUrlTemplate: String = "https://chzzk.naver.com/{channelId}",
    var publishUrlTemplate: String = "",
    var oauthStateTtlSeconds: Long = 600,
    var tokenRefreshBufferSeconds: Long = 60
)
