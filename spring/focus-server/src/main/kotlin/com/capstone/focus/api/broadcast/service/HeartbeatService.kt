package com.capstone.focus.api.broadcast.service

import com.capstone.focus.common.external.redis.RedisService
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

interface HeartbeatService {
    fun viewerHeartbeat(broadcastId: String, viewerId: String)
    fun streamerHeartbeat(broadcastId: String, streamerId: String)
}

@Service
class HeartbeatServiceImpl(
    private val redisService: RedisService
) : HeartbeatService {

    override fun viewerHeartbeat(broadcastId: String, viewerId: String) {
        val key = "broadcast:viewers:$broadcastId"
        val currentTime = System.currentTimeMillis().toDouble()

        redisService.addToZSet(key, viewerId, currentTime)
        redisService.expireKey(key, 2L, TimeUnit.HOURS)
    }

    override fun streamerHeartbeat(broadcastId: String, streamerId: String) {
        val key = "broadcast:streamer:$broadcastId"
        val currentTime = System.currentTimeMillis().toString()

        redisService.setValueWithTTL(key, currentTime, 30L, TimeUnit.SECONDS)
    }
}