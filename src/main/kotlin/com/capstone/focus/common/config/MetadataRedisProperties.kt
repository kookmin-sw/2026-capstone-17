package com.capstone.focus.common.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "focus.metadata.redis")
data class MetadataRedisProperties(
    var keyTemplate: String = "broadcast:{broadcast_id}:meta:{pts_us}",
    var ttlSeconds: Long = 60
)
