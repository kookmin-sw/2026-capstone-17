package com.capstone.focus.domain.repository

import com.capstone.focus.domain.entity.TrackingSession
import org.springframework.data.jpa.repository.JpaRepository

interface TrackingSessionRepository : JpaRepository<TrackingSession, String> {
    fun countByBroadcastId(broadcastId: String): Long
}
