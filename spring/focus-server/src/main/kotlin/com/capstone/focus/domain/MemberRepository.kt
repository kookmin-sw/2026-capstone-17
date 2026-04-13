package com.capstone.focus.domain


import com.capstone.focus.domain.entity.Member
import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository : JpaRepository<Member, String> {
    // 카카오 ID로 회원 조회 (로그인 핵심)
    fun findByKakaoId(kakaoId: Long): Member?

    // 이메일 중복 확인용
    fun existsByEmail(email: String): Boolean

    // 카카오 ID 존재 여부 확인
    fun existsByKakaoId(kakaoId: Long): Boolean
}