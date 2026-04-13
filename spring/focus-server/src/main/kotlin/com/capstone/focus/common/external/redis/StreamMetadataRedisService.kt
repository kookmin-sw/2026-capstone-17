package com.capstone.focus.common.external.redis

import com.capstone.focus.common.config.MetadataRedisProperties
import com.capstone.focus.common.external.redis.model.FaceMetadataRedisPayload
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class StreamMetadataRedisService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val metadataRedisProperties: MetadataRedisProperties
) {

    fun saveFaceMetadata(payload: FaceMetadataRedisPayload) {
        val redisKey = buildKey(sessionId = payload.sessionId, ptsUs = payload.ptsUs)
        val serializedPayload = objectMapper.writeValueAsString(payload)
        if (metadataRedisProperties.ttlSeconds > 0L) {
            redisTemplate.opsForValue().set(redisKey, serializedPayload, metadataRedisProperties.ttlSeconds, TimeUnit.SECONDS)
            return
        }
        redisTemplate.opsForValue().set(redisKey, serializedPayload)
    }

    private fun buildKey(sessionId: String, ptsUs: Long): String {
        return metadataRedisProperties.keyTemplate
            .replace("{broadcast_id}", sessionId)
            .replace("{session_id}", sessionId)
            .replace("{stream_id}", sessionId)
            .replace("{pts_us}", ptsUs.toString())
    }
}
