package com.capstone.focus.domain

import com.capstone.focus.auth.dto.response.UserInfoResponse
import com.capstone.focus.common.exception.ApiException
import com.capstone.focus.common.exception.ErrorTitle
import com.capstone.focus.domain.entity.Member
import com.capstone.focus.domain.entity.enum.MemberRole
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

interface MemberService {
    fun getMemberInfo(memberId: String): UserInfoResponse
    fun getMemberById(memberId: String): Member
    fun getMemberByKakaoId(kakaoId: Long): Member?
    fun createOrUpdateKakaoMember(kakaoId: Long, nickname: String, email: String?, profileImageUrl: String?): Member
}

@Service
@Transactional(readOnly = true)
class MemberServiceImpl(
    private val memberRepository: MemberRepository
) : MemberService {

    override fun getMemberInfo(memberId: String): UserInfoResponse {
        val findMember = getMemberById(memberId)

        return UserInfoResponse(
            id = findMember.id,
            kakaoId = findMember.kakaoId,
            name = findMember.nickname ?: "FocusUser",
            email = findMember.email,
            profileImageUrl = null
        )
    }

    override fun getMemberById(memberId: String): Member {
        return memberRepository.findByIdOrNull(memberId)
            ?: throw ApiException(ErrorTitle.NotFoundUser)
    }

    override fun getMemberByKakaoId(kakaoId: Long): Member? = memberRepository.findByKakaoId(kakaoId)

    @Transactional
    override fun createOrUpdateKakaoMember(
        kakaoId: Long,
        nickname: String,
        email: String?,
        profileImageUrl: String?
    ): Member {
        val existingMember = memberRepository.findByKakaoId(kakaoId)

        return if (existingMember != null) {
            existingMember.updateProfile(
                nickname = nickname,
                faceEmbedding = existingMember.faceEmbedding
            )
            existingMember
        } else {
            val newMember = Member(
                kakaoId = kakaoId,
                email = email,
                nickname = nickname,
                role = MemberRole.USER,
                faceEmbedding = null
            )
            memberRepository.save(newMember)
        }
    }
}
