package com.capstone.focus.api.broadcast.service

import com.capstone.focus.api.broadcast.dto.request.CreateBroadcastRequest
import com.capstone.focus.api.broadcast.dto.request.UpdateBroadcastRequest
import com.capstone.focus.api.broadcast.dto.response.BroadcastResponse
import com.capstone.focus.common.exception.ApiException
import com.capstone.focus.common.exception.ErrorTitle
import com.capstone.focus.domain.MemberRepository
import com.capstone.focus.domain.entity.Broadcast
import com.capstone.focus.domain.repository.BroadcastRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface BroadcastService {
    fun createBroadcast(memberId: String, request: CreateBroadcastRequest): BroadcastResponse
    fun getBroadcastList(pageable: Pageable): Page<BroadcastResponse>
    fun getBroadcastDetail(broadcastId: String): BroadcastResponse
    fun updateBroadcast(memberId: String, broadcastId: String, request: UpdateBroadcastRequest): BroadcastResponse
    fun deleteBroadcast(memberId: String, broadcastId: String)
}

@Service
class BroadcastServiceImpl(
    private val broadcastRepository: BroadcastRepository,
    private val memberRepository: MemberRepository
) : BroadcastService {

    // 방송 생성
    @Transactional
    override fun createBroadcast(memberId: String, request: CreateBroadcastRequest): BroadcastResponse {
        val member = memberRepository.findByIdOrNull(memberId)
            ?: throw ApiException(ErrorTitle.NotFoundUser)

        val streamKey = UUID.randomUUID().toString()

        val broadcast = Broadcast(
            member = member,
            streamKey = streamKey,
            title = request.title
        )

        val savedBroadcast = broadcastRepository.save(broadcast)
        return BroadcastResponse.from(savedBroadcast)
    }

    // 방송 리스트 조회 (페이징)
    @Transactional(readOnly = true)
    override fun getBroadcastList(pageable: Pageable): Page<BroadcastResponse> {
        return broadcastRepository.findAllByDeletedAtIsNull(pageable)
            .map { BroadcastResponse.from(it) }
    }

    // 방송 상세 조회
    @Transactional(readOnly = true)
    override fun getBroadcastDetail(broadcastId: String): BroadcastResponse {
        val broadcast = broadcastRepository.findByIdAndDeletedAtIsNull(broadcastId)
            ?: throw ApiException(ErrorTitle.NotFoundBroadcast)

        return BroadcastResponse.from(broadcast)
    }

    // 방송 정보 수정- 소유자 확인 필수
    @Transactional
    override fun updateBroadcast(
        memberId: String,
        broadcastId: String,
        request: UpdateBroadcastRequest
    ): BroadcastResponse {
        val broadcast = broadcastRepository.findByIdAndDeletedAtIsNull(broadcastId)
            ?: throw ApiException(ErrorTitle.NotFoundBroadcast)

        // 소유권 검증
        validateOwnership(broadcast, memberId)

        // 엔티티 메서드 호출
        broadcast.updateTitle(request.title)

        return BroadcastResponse.from(broadcast)
    }

    // 방송 삭제 - 소유자 확인 필수
    @Transactional
    override fun deleteBroadcast(memberId: String, broadcastId: String) {
        val broadcast = broadcastRepository.findByIdAndDeletedAtIsNull(broadcastId)
            ?: throw ApiException(ErrorTitle.NotFoundBroadcast)

        validateOwnership(broadcast, memberId)

        broadcastRepository.delete(broadcast)
    }

    // 내부 검증 로직: 방송 주인인지 확인
    private fun validateOwnership(broadcast: Broadcast, memberId: String) {
        if (broadcast.member.id != memberId) {
            throw ApiException(ErrorTitle.Forbidden)
        }
    }
}