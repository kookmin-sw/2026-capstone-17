package com.capstone.focus.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI

@Configuration
class S3Config(
    private val s3Properties: S3Properties
) {

    @Bean
    fun s3Client(): S3Client {
        val builder = S3Client.builder()
            .region(Region.of(s3Properties.region.ifBlank { "ap-northeast-2" }))

        if (s3Properties.accessKey.isNotBlank() && s3Properties.secretKey.isNotBlank()) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(s3Properties.accessKey, s3Properties.secretKey)
                )
            )
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create())
        }

        if (s3Properties.endpoint.isNotBlank()) {
            builder.endpointOverride(URI.create(s3Properties.endpoint))
        }

        return builder.build()
    }
}
