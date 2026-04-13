package com.capstone.focus.domain.repository

import com.capstone.focus.domain.entity.Broadcast
import com.capstone.focus.domain.entity.enum.BroadcastStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface BroadcastRepository : JpaRepository<Broadcast, String> {

    // 단건 조회
    fun findByIdAndDeletedAtIsNull(id: String): Broadcast?

    // 페이징 조회
    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<Broadcast>

    // 방송 상태별 조회 필요 시 사용
    fun findAllByStatusAndDeletedAtIsNull(status: BroadcastStatus, pageable: Pageable): Page<Broadcast>
}