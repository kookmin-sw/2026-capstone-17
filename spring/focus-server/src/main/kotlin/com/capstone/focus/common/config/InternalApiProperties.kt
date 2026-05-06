package com.capstone.focus.common.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "focus.internal-api")
data class InternalApiProperties(
    var key: String = ""
)
