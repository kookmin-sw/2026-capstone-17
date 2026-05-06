package com.capstone.focus.common.config

import com.capstone.focus.domain.entity.enum.BroadcastOutputMode
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "focus.broadcast")
data class BroadcastOutputProperties(
    var outputMode: BroadcastOutputMode = BroadcastOutputMode.CHZZK_RTMP,
    var fallbackToHls: Boolean = true
)
