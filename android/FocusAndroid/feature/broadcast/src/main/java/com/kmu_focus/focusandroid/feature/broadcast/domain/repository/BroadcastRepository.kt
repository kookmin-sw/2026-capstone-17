package com.kmu_focus.focusandroid.feature.broadcast.domain.repository

import com.kmu_focus.focusandroid.feature.broadcast.domain.entity.Broadcast

interface BroadcastRepository {
    suspend fun createBroadcast(title: String): Result<Broadcast>

    suspend fun startBroadcast(
        broadcastId: String,
        avatarId: String,
    ): Result<Broadcast>

    suspend fun stopBroadcast(broadcastId: String): Result<Broadcast>

    suspend fun getBroadcastList(
        page: Int,
        size: Int,
    ): Result<List<Broadcast>>

    suspend fun getBroadcastDetail(broadcastId: String): Result<Broadcast>

    suspend fun updateBroadcast(
        broadcastId: String,
        title: String,
    ): Result<Broadcast>

    suspend fun deleteBroadcast(broadcastId: String): Result<Unit>

    suspend fun sendStreamerHeartbeat(broadcastId: String): Result<Unit>
}
