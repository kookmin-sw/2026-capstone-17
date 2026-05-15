package com.capstone.focus.domain.repository

import com.capstone.focus.domain.entity.Broadcast
import com.capstone.focus.domain.entity.enum.BroadcastStatus
import com.capstone.focus.domain.entity.enum.StreamingPlatform
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface BroadcastRepository : JpaRepository<Broadcast, String> {
    fun findByIdAndDeletedAtIsNull(id: String): Broadcast?

    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<Broadcast>

    fun findAllByStatusAndDeletedAtIsNull(status: BroadcastStatus, pageable: Pageable): Page<Broadcast>

    fun findAllByStatusAndPlatformAndDeletedAtIsNull(
        status: BroadcastStatus,
        platform: StreamingPlatform
    ): List<Broadcast>
}
