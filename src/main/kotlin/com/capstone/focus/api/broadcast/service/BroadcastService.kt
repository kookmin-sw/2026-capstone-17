package com.capstone.focus.api.broadcast.service

import com.capstone.focus.api.analysis.service.BroadcastAnalysisService
import com.capstone.focus.api.broadcast.dto.request.CreateBroadcastRequest
import com.capstone.focus.api.broadcast.dto.request.StartBroadcastRequest
import com.capstone.focus.api.broadcast.dto.request.UpdateBroadcastRequest
import com.capstone.focus.api.broadcast.dto.response.BroadcastResponse
import com.capstone.focus.common.exception.ApiException
import com.capstone.focus.common.exception.ErrorTitle
import com.capstone.focus.common.external.fastapi.FastApiStreamClient
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
    fun startBroadcast(memberId: String, broadcastId: String, request: StartBroadcastRequest): BroadcastResponse
    fun stopBroadcast(memberId: String, broadcastId: String): BroadcastResponse
    fun getBroadcastList(pageable: Pageable): Page<BroadcastResponse>
    fun getBroadcastDetail(broadcastId: String): BroadcastResponse
    fun updateBroadcast(memberId: String, broadcastId: String, request: UpdateBroadcastRequest): BroadcastResponse
    fun deleteBroadcast(memberId: String, broadcastId: String)
}

@Service
class BroadcastServiceImpl(
    private val broadcastRepository: BroadcastRepository,
    private val memberRepository: MemberRepository,
    private val fastApiStreamClient: FastApiStreamClient,
    private val broadcastAnalysisService: BroadcastAnalysisService
) : BroadcastService {

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

    @Transactional
    override fun startBroadcast(
        memberId: String,
        broadcastId: String,
        request: StartBroadcastRequest
    ): BroadcastResponse {
        val broadcast = broadcastRepository.findByIdAndDeletedAtIsNull(broadcastId)
            ?: throw ApiException(ErrorTitle.NotFoundBroadcast)

        validateOwnership(broadcast, memberId)

        val worker = fastApiStreamClient.startBroadcast(
            broadcastId = broadcast.id,
            streamKey = broadcast.streamKey,
            avatarId = request.avatarId
        )

        broadcast.startBroadcast(worker.hlsUrl)

        return BroadcastResponse.from(broadcast)
    }

    @Transactional
    override fun stopBroadcast(memberId: String, broadcastId: String): BroadcastResponse {
        val broadcast = broadcastRepository.findByIdAndDeletedAtIsNull(broadcastId)
            ?: throw ApiException(ErrorTitle.NotFoundBroadcast)

        validateOwnership(broadcast, memberId)

        fastApiStreamClient.stopBroadcast(broadcast.id)
        broadcast.endBroadcast()
        broadcastAnalysisService.queuePostStreamSummary(memberId, broadcast.id)

        return BroadcastResponse.from(broadcast)
    }

    @Transactional(readOnly = true)
    override fun getBroadcastList(pageable: Pageable): Page<BroadcastResponse> {
        return broadcastRepository.findAllByDeletedAtIsNull(pageable)
            .map { BroadcastResponse.from(it) }
    }

    @Transactional(readOnly = true)
    override fun getBroadcastDetail(broadcastId: String): BroadcastResponse {
        val broadcast = broadcastRepository.findByIdAndDeletedAtIsNull(broadcastId)
            ?: throw ApiException(ErrorTitle.NotFoundBroadcast)

        return BroadcastResponse.from(broadcast)
    }

    @Transactional
    override fun updateBroadcast(
        memberId: String,
        broadcastId: String,
        request: UpdateBroadcastRequest
    ): BroadcastResponse {
        val broadcast = broadcastRepository.findByIdAndDeletedAtIsNull(broadcastId)
            ?: throw ApiException(ErrorTitle.NotFoundBroadcast)

        validateOwnership(broadcast, memberId)
        broadcast.updateTitle(request.title)

        return BroadcastResponse.from(broadcast)
    }

    @Transactional
    override fun deleteBroadcast(memberId: String, broadcastId: String) {
        val broadcast = broadcastRepository.findByIdAndDeletedAtIsNull(broadcastId)
            ?: throw ApiException(ErrorTitle.NotFoundBroadcast)

        validateOwnership(broadcast, memberId)
        broadcastRepository.delete(broadcast)
    }

    private fun validateOwnership(broadcast: Broadcast, memberId: String) {
        if (broadcast.member.id != memberId) {
            throw ApiException(ErrorTitle.Forbidden)
        }
    }
}
