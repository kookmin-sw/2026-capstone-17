package com.capstone.focus.api.broadcast.service

import com.capstone.focus.api.analysis.service.BroadcastAnalysisService
import com.capstone.focus.api.broadcast.dto.request.CreateBroadcastRequest
import com.capstone.focus.api.broadcast.dto.request.StartBroadcastRequest
import com.capstone.focus.api.broadcast.dto.request.UpdateBroadcastRequest
import com.capstone.focus.api.broadcast.dto.response.BroadcastResponse
import com.capstone.focus.api.platform.service.ChzzkPlatformService
import com.capstone.focus.common.config.BroadcastOutputProperties
import com.capstone.focus.common.exception.ApiException
import com.capstone.focus.common.exception.ErrorTitle
import com.capstone.focus.common.external.fastapi.FastApiStreamClient
import com.capstone.focus.domain.MemberRepository
import com.capstone.focus.domain.entity.Broadcast
import com.capstone.focus.domain.entity.enum.BroadcastOutputMode
import com.capstone.focus.domain.entity.enum.StreamingPlatform
import com.capstone.focus.domain.repository.BroadcastRepository
import org.slf4j.LoggerFactory
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
    private val broadcastAnalysisService: BroadcastAnalysisService,
    private val chzzkPlatformService: ChzzkPlatformService,
    private val broadcastOutputProperties: BroadcastOutputProperties
) : BroadcastService {
    private val logger = LoggerFactory.getLogger(BroadcastServiceImpl::class.java)

    @Transactional
    override fun createBroadcast(memberId: String, request: CreateBroadcastRequest): BroadcastResponse {
        val member = memberRepository.findByIdOrNull(memberId)
            ?: throw ApiException(ErrorTitle.NotFoundUser)

        val broadcast = Broadcast(
            member = member,
            streamKey = UUID.randomUUID().toString(),
            title = request.title,
            platform = StreamingPlatform.CHZZK,
            outputMode = broadcastOutputProperties.outputMode
        )

        val savedBroadcast = broadcastRepository.save(broadcast)
        return BroadcastResponse.from(savedBroadcast)
    }

    @Transactional(noRollbackFor = [ApiException::class])
    override fun startBroadcast(
        memberId: String,
        broadcastId: String,
        request: StartBroadcastRequest
    ): BroadcastResponse {
        val broadcast = broadcastRepository.findByIdAndDeletedAtIsNull(broadcastId)
            ?: throw ApiException(ErrorTitle.NotFoundBroadcast)

        validateOwnership(broadcast, memberId)

        return if (broadcastOutputProperties.outputMode == BroadcastOutputMode.HLS) {
            startHlsBroadcast(broadcast, request, null)
        } else {
            startPlatformBroadcast(memberId, broadcast, request)
        }
    }

    private fun startPlatformBroadcast(
        memberId: String,
        broadcast: Broadcast,
        request: StartBroadcastRequest
    ): BroadcastResponse {
        return try {
            when (broadcastOutputProperties.outputMode) {
                BroadcastOutputMode.CHZZK_RTMP -> startChzzkBroadcast(memberId, broadcast, request)
                BroadcastOutputMode.HLS -> startHlsBroadcast(broadcast, request, null)
            }
        } catch (exception: ApiException) {
            broadcast.markStartFailure(exception.message)
            if (!broadcastOutputProperties.fallbackToHls) {
                throw exception
            }
            logger.warn("Platform broadcast start failed. Falling back to HLS. broadcastId={}", broadcast.id, exception)
            startHlsBroadcast(broadcast, request, exception.message)
        }
    }

    private fun startChzzkBroadcast(
        memberId: String,
        broadcast: Broadcast,
        request: StartBroadcastRequest
    ): BroadcastResponse {
        val chzzkTarget = chzzkPlatformService.prepareBroadcastTarget(memberId, broadcast)
        val worker = fastApiStreamClient.startBroadcast(
            broadcastId = broadcast.id,
            inputStreamKey = broadcast.streamKey,
            avatarId = request.avatarId,
            outputMode = BroadcastOutputMode.CHZZK_RTMP.name,
            outputUrl = chzzkTarget.outputUrl,
            watchUrl = chzzkTarget.watchUrl
        )
        broadcast.startBroadcast(
            platform = StreamingPlatform.CHZZK,
            platformChannelId = chzzkTarget.platformChannelId,
            watchUrl = worker.watchUrl ?: chzzkTarget.watchUrl,
            outputMode = BroadcastOutputMode.CHZZK_RTMP,
            hlsUrl = null
        )
        return BroadcastResponse.from(broadcast)
    }

    private fun startHlsBroadcast(
        broadcast: Broadcast,
        request: StartBroadcastRequest,
        fallbackReason: String?
    ): BroadcastResponse {
        val worker = fastApiStreamClient.startBroadcast(
            broadcastId = broadcast.id,
            inputStreamKey = broadcast.streamKey,
            avatarId = request.avatarId,
            outputMode = BroadcastOutputMode.HLS.name,
            outputUrl = null,
            watchUrl = null
        )
        broadcast.startBroadcast(
            platform = StreamingPlatform.CHZZK,
            platformChannelId = null,
            watchUrl = worker.watchUrl,
            outputMode = BroadcastOutputMode.HLS,
            hlsUrl = worker.watchUrl ?: worker.outputUrl
        )
        if (fallbackReason != null) {
            broadcast.markStartFailure("Platform start failed, HLS fallback started: $fallbackReason")
        }
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
