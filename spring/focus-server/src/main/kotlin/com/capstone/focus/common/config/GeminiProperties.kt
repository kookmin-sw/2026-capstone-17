package com.capstone.focus.common.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "google.gemini")
data class GeminiProperties(
    var apiKey: String = "",
    var model: String = "gemini-2.5-flash-lite",
    var baseUrl: String = "https://generativelanguage.googleapis.com"
)
