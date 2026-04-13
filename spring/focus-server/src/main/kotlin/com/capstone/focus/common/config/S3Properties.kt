package com.capstone.focus.common.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "aws.s3")
data class S3Properties(
    var bucket: String = "",
    var region: String = "ap-northeast-2",
    var accessKey: String = "",
    var secretKey: String = "",
    var endpoint: String = "",
    var publicBaseUrl: String = ""
)
