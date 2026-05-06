package com.capstone.focus.domain

import com.capstone.focus.domain.entity.Member
import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository : JpaRepository<Member, String> {
    fun findByKakaoId(kakaoId: Long): Member?
    fun existsByEmail(email: String): Boolean
    fun existsByKakaoId(kakaoId: Long): Boolean
}
