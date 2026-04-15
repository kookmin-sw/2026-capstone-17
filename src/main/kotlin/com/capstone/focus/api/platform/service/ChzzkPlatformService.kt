package com.capstone.focus.api.platform.service

import com.capstone.focus.api.platform.dto.response.ChzzkConnectResponse
import com.capstone.focus.api.platform.dto.response.ChzzkConnectionStatusResponse
import com.capstone.focus.common.config.ChzzkProperties
import com.capstone.focus.common.exception.ApiException
import com.capstone.focus.common.exception.ErrorTitle
import com.capstone.focus.common.external.chzzk.ChzzkClient
import com.capstone.focus.common.external.chzzk.dto.ChzzkTokenContent
import com.capstone.focus.common.external.redis.RedisService
import com.capstone.focus.domain.MemberRepository
import com.capstone.focus.domain.entity.Broadcast
import com.capstone.focus.domain.entity.StreamingPlatformConnection
import com.capstone.focus.domain.entity.enum.StreamingPlatform
import com.capstone.focus.domain.repository.StreamingPlatformConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

interface ChzzkPlatformService {
    fun createConnectUrl(memberId: String): ChzzkConnectResponse
    fun handleCallback(code: String, state: String): ChzzkConnectionStatusResponse
    fun getConnectionStatus(memberId: String): ChzzkConnectionStatusResponse
    fun disconnect(memberId: String)
    fun prepareBroadcastTarget(memberId: String, broadcast: Broadcast): ChzzkBroadcastTarget
}

data class ChzzkBroadcastTarget(
    val platformChannelId: String,
    val watchUrl: String,
    val outputUrl: String
)

@Service
class ChzzkPlatformServiceImpl(
    private val memberRepository: MemberRepository,
    private val redisService: RedisService,
    private val chzzkProperties: ChzzkProperties,
    private val chzzkClient: ChzzkClient,
    private val connectionRepository: StreamingPlatformConnectionRepository
) : ChzzkPlatformService {
    private val logger = LoggerFactory.getLogger(ChzzkPlatformServiceImpl::class.java)

    @Transactional(readOnly = true)
    override fun createConnectUrl(memberId: String): ChzzkConnectResponse {
        validateOAuthConfiguration()
        ensureMemberExists(memberId)
        val state = UUID.randomUUID().toString()
        redisService.setValueWithTTL(
            key = stateKey(state),
            value = memberId,
            timeout = chzzkProperties.oauthStateTtlSeconds,
            timeUnit = TimeUnit.SECONDS
        )
        return ChzzkConnectResponse(authUrl = chzzkClient.getAuthorizationUrl(state))
    }

    @Transactional
    override fun handleCallback(code: String, state: String): ChzzkConnectionStatusResponse {
        validateOAuthConfiguration()
        val memberId = redisService.getValue(stateKey(state))
            ?: throw ApiException(ErrorTitle.BadRequest, "유효하지 않거나 만료된 치지직 OAuth state 입니다.")

        val member = memberRepository.findByIdOrNull(memberId)
            ?: throw ApiException(ErrorTitle.NotFoundUser)

        val token = chzzkClient.issueToken(code = code, state = state)
        val me = chzzkClient.getCurrentUser(token.accessToken)

        val connection = connectionRepository.findByMember_IdAndPlatform(member.id, StreamingPlatform.CHZZK)
            ?.also {
                it.reconnect(
                    platformUserId = me.channelId,
                    platformChannelId = me.channelId,
                    platformChannelName = me.channelName,
                    accessToken = token.accessToken,
                    refreshToken = token.refreshToken,
                    accessTokenExpiresAt = token.toAccessTokenExpiresAt()
                )
            }
            ?: StreamingPlatformConnection(
                member = member,
                platform = StreamingPlatform.CHZZK,
                platformUserId = me.channelId,
                platformChannelId = me.channelId,
                platformChannelName = me.channelName,
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                accessTokenExpiresAt = token.toAccessTokenExpiresAt()
            )

        connectionRepository.save(connection)
        redisService.deleteKey(stateKey(state))

        return connection.toStatusResponse(buildWatchUrl(connection.platformChannelId))
    }

    @Transactional(readOnly = true)
    override fun getConnectionStatus(memberId: String): ChzzkConnectionStatusResponse {
        ensureMemberExists(memberId)
        val connection = connectionRepository.findByMember_IdAndPlatformAndRevokedAtIsNull(memberId, StreamingPlatform.CHZZK)
            ?: return ChzzkConnectionStatusResponse(connected = false)

        return connection.toStatusResponse(buildWatchUrl(connection.platformChannelId))
    }

    @Transactional
    override fun disconnect(memberId: String) {
        val connection = connectionRepository.findByMember_IdAndPlatformAndRevokedAtIsNull(memberId, StreamingPlatform.CHZZK)
            ?: return

        try {
            chzzkClient.revokeToken(connection.accessToken, tokenTypeHint = "access_token")
        } catch (exception: Exception) {
            logger.warn("Failed to revoke CHZZK token. memberId={}, connectionId={}", memberId, connection.id, exception)
        }

        connection.revoke()
        connectionRepository.save(connection)
    }

    @Transactional
    override fun prepareBroadcastTarget(memberId: String, broadcast: Broadcast): ChzzkBroadcastTarget {
        validateOAuthConfiguration()
        val connection = connectionRepository.findByMember_IdAndPlatformAndRevokedAtIsNull(memberId, StreamingPlatform.CHZZK)
            ?: throw ApiException(ErrorTitle.BadRequest, "치지직 채널 연동이 필요합니다.")

        if (connection.needsRefresh(chzzkProperties.tokenRefreshBufferSeconds)) {
            val refreshedToken = try {
                chzzkClient.refreshToken(connection.refreshToken)
            } catch (exception: ApiException) {
                connection.revoke()
                connectionRepository.save(connection)
                throw ApiException(ErrorTitle.InvalidToken, "치지직 토큰이 만료되었거나 재발급에 실패했습니다. 다시 연동해 주세요.")
            }

            connection.updateTokens(
                accessToken = refreshedToken.accessToken,
                refreshToken = refreshedToken.refreshToken,
                accessTokenExpiresAt = refreshedToken.toAccessTokenExpiresAt()
            )
            connectionRepository.save(connection)
        }

        if (!broadcast.title.isNullOrBlank()) {
            try {
                chzzkClient.updateLiveTitle(connection.accessToken, broadcast.title!!)
            } catch (exception: ApiException) {
                logger.warn("Failed to update CHZZK live title. broadcastId={}", broadcast.id, exception)
            }
        }

        val streamKey = chzzkClient.getStreamKey(connection.accessToken).streamKey

        return ChzzkBroadcastTarget(
            platformChannelId = connection.platformChannelId,
            watchUrl = buildWatchUrl(connection.platformChannelId),
            outputUrl = buildPublishUrl(connection.platformChannelId, streamKey)
        )
    }

    private fun ensureMemberExists(memberId: String) {
        if (!memberRepository.existsById(memberId)) {
            throw ApiException(ErrorTitle.NotFoundUser)
        }
    }

    private fun buildWatchUrl(channelId: String): String {
        return chzzkProperties.watchUrlTemplate.replace("{channelId}", channelId)
    }

    private fun buildPublishUrl(channelId: String, streamKey: String): String {
        val template = chzzkProperties.publishUrlTemplate.trim()
        if (template.isBlank()) {
            throw ApiException(
                ErrorTitle.BadRequest,
                "치지직 송출 URL 템플릿이 설정되지 않았습니다. CHZZK_STREAM_PUBLISH_URL_TEMPLATE 값을 확인해 주세요."
            )
        }
        if (!template.contains("{streamKey}")) {
            throw ApiException(
                ErrorTitle.BadRequest,
                "치지직 송출 URL 템플릿에는 {streamKey} 플레이스홀더가 포함되어야 합니다."
            )
        }
        return template
            .replace("{channelId}", channelId)
            .replace("{streamKey}", streamKey)
    }

    private fun validateOAuthConfiguration() {
        if (chzzkProperties.clientId.isBlank() || chzzkProperties.clientSecret.isBlank() || chzzkProperties.redirectUri.isBlank()) {
            throw ApiException(
                ErrorTitle.BadRequest,
                "치지직 OAuth 설정이 비어 있습니다. clientId, clientSecret, redirectUri 값을 확인해 주세요."
            )
        }
    }

    private fun stateKey(state: String): String = "oauth:state:chzzk:$state"

    private fun StreamingPlatformConnection.toStatusResponse(watchUrl: String): ChzzkConnectionStatusResponse {
        return ChzzkConnectionStatusResponse(
            connected = !isRevoked(),
            channelId = platformChannelId,
            channelName = platformChannelName,
            watchUrl = watchUrl,
            accessTokenExpiresAt = accessTokenExpiresAt,
            connectedAt = connectedAt
        )
    }

    private fun ChzzkTokenContent.toAccessTokenExpiresAt(): LocalDateTime {
        val expiresInSeconds = expiresIn.toLongOrNull()
            ?: throw ApiException(ErrorTitle.ExternalServerError, "치지직 토큰 만료 시간이 올바르지 않습니다.")
        return LocalDateTime.now().plusSeconds(expiresInSeconds)
    }
}
