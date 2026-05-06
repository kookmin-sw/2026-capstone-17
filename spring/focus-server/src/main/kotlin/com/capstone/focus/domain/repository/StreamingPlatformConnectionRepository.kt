package com.capstone.focus.domain.repository

import com.capstone.focus.domain.entity.StreamingPlatformConnection
import com.capstone.focus.domain.entity.enum.StreamingPlatform
import org.springframework.data.jpa.repository.JpaRepository

interface StreamingPlatformConnectionRepository : JpaRepository<StreamingPlatformConnection, String> {
    fun findByMember_IdAndPlatform(memberId: String, platform: StreamingPlatform): StreamingPlatformConnection?
    fun findByMember_IdAndPlatformAndRevokedAtIsNull(
        memberId: String,
        platform: StreamingPlatform
    ): StreamingPlatformConnection?
}
