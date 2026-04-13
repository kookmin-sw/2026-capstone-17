package com.capstone.focus.common.config
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "kakao.oauth")
data class KakaoOAuthConfig(
    var clientId: String = "",
    var clientSecret: String = "",
    var redirectUri: String = "",
    var authorizationUri: String = "",
    var tokenUri: String = "",
    var userInfoUri: String = ""
)