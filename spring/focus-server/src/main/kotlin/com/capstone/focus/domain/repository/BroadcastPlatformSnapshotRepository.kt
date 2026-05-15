package com.capstone.focus.domain.repository

import com.capstone.focus.domain.entity.BroadcastPlatformSnapshot
import org.springframework.data.jpa.repository.JpaRepository

interface BroadcastPlatformSnapshotRepository : JpaRepository<BroadcastPlatformSnapshot, String> {
    fun findAllByBroadcastIdOrderBySampledAtAsc(broadcastId: String): List<BroadcastPlatformSnapshot>
    fun findTopByBroadcastIdOrderBySampledAtDesc(broadcastId: String): BroadcastPlatformSnapshot?
}
