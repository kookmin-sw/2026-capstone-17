package com.capstone.focus.common.external.redis

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class RedisService(
    private val redisTemplate: RedisTemplate<String, String>
) {

    /**
     * Refresh Token 저장 (양방향 매핑)
     * 1. userId -> refreshToken 매핑
     * 2. refreshToken -> userId 매핑
     */
    fun saveRefreshToken(userId: String, refreshToken: String, timeout: Long, timeUnit: TimeUnit) {
        val userToTokenKey = "refresh_token:user:$userId"
        val tokenToUserKey = "refresh_token:token:$refreshToken"

        // 기존 사용자의 토큰이 있다면 제거
        val existingToken = redisTemplate.opsForValue().get(userToTokenKey)
        if (existingToken != null) {
            redisTemplate.delete("refresh_token:token:$existingToken")
        }

        // 새로운 매핑 저장
        redisTemplate.opsForValue().set(userToTokenKey, refreshToken, timeout, timeUnit)
        redisTemplate.opsForValue().set(tokenToUserKey, userId, timeout, timeUnit)
    }

    /**
     * 사용자 ID로 Refresh Token 조회
     */
    fun getRefreshToken(userId: String): String? {
        val key = "refresh_token:user:$userId"
        return redisTemplate.opsForValue().get(key)
    }

    /**
     * Refresh Token으로 사용자 ID 조회 (새로운 방식)
     */
    fun getUserIdByRefreshToken(refreshToken: String): String? {
        val key = "refresh_token:token:$refreshToken"
        return redisTemplate.opsForValue().get(key)
    }

    /**
     * 사용자 ID로 Refresh Token 삭제
     */
    fun deleteRefreshTokenByUserId(userId: String) {
        val userToTokenKey = "refresh_token:user:$userId"
        val refreshToken = redisTemplate.opsForValue().get(userToTokenKey)

        if (refreshToken != null) {
            val tokenToUserKey = "refresh_token:token:$refreshToken"
            redisTemplate.delete(userToTokenKey)
            redisTemplate.delete(tokenToUserKey)
        }
    }

    /**
     * Refresh Token으로 직접 삭제 (Token Rotation용)
     */
    fun deleteRefreshToken(refreshToken: String) {
        val tokenToUserKey = "refresh_token:token:$refreshToken"
        val userId = redisTemplate.opsForValue().get(tokenToUserKey)

        if (userId != null) {
            val userToTokenKey = "refresh_token:user:$userId"
            redisTemplate.delete(userToTokenKey)
            redisTemplate.delete(tokenToUserKey)
        }
    }

    /**
     * 범용: ZSET에 데이터 추가 (Score 기준 정렬)
     */
    fun addToZSet(key: String, value: String, score: Double) {
        redisTemplate.opsForZSet().add(key, value, score)
    }

    /**
     * 범용: Key의 만료 시간(TTL) 설정
     */
    fun expireKey(key: String, timeout: Long, timeUnit: TimeUnit) {
        redisTemplate.expire(key, timeout, timeUnit)
    }

    /**
     * 범용: Key-Value 저장 및 만료 시간 설정
     */
    fun setValueWithTTL(key: String, value: String, timeout: Long, timeUnit: TimeUnit) {
        redisTemplate.opsForValue().set(key, value, timeout, timeUnit)
    }
}