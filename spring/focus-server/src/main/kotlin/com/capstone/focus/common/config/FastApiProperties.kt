package com.capstone.focus.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "focus.fastapi")
data class FastApiProperties(
    var baseUrl: String = "http://localhost:8000"
)
