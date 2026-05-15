package com.capstone.focus.common.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "focus.analysis.snapshot")
data class AnalysisSnapshotProperties(
    var enabled: Boolean = true,
    var fixedDelayMs: Long = 60_000,
    var liveListPageSize: Int = 20,
    var liveListMaxPages: Int = 10
) {
    fun sampleDurationSec(): Long = (fixedDelayMs / 1000).coerceAtLeast(1)
}
