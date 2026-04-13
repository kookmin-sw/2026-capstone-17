package com.capstone.focus.common.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class RedisConfig(
    @Value("\${spring.data.redis.host:localhost}")
    private val host: String,
    @Value("\${spring.data.redis.port:6379}")
    private val port: Int,
    @Value("\${spring.data.redis.ssl.enabled:false}")
    private val sslEnabled: Boolean
) {

    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        val config = RedisStandaloneConfiguration(host, port)

        val clientConfig = if (sslEnabled) {
            LettuceClientConfiguration.builder().useSsl().build()
        } else {
            LettuceClientConfiguration.builder().build()
        }

        return LettuceConnectionFactory(config, clientConfig)
    }

    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, String> {
        val template = RedisTemplate<String, String>()
        template.connectionFactory = connectionFactory
        template.setKeySerializer(StringRedisSerializer())
        template.setValueSerializer(StringRedisSerializer())
        return template
    }
}