package com.capstone.focus.api.platform.service

import com.capstone.focus.api.platform.dto.response.YoutubeConnectResponse
import com.capstone.focus.api.platform.dto.response.YoutubeConnectionStatusResponse
import com.capstone.focus.common.config.YoutubeProperties
import com.capstone.focus.common.exception.ApiException
import com.capstone.focus.common.exception.ErrorTitle
import com.capstone.focus.common.external.redis.RedisService
import com.capstone.focus.common.external.youtube.YoutubeApiFeignClient
import com.capstone.focus.common.external.youtube.YoutubeOAuthClient
import com.capstone.focus.common.external.youtube.dto.YoutubeIngestionInfo
import com.capstone.focus.common.external.youtube.dto.YoutubeLiveBroadcastContentDetails
import com.capstone.focus.common.external.youtube.dto.YoutubeLiveBroadcastInsertRequest
import com.capstone.focus.common.external.youtube.dto.YoutubeLiveBroadcastSnippet
import com.capstone.focus.common.external.youtube.dto.YoutubeLiveBroadcastStatus
import com.capstone.focus.common.external.youtube.dto.YoutubeLiveStreamCdn
import com.capstone.focus.common.external.youtube.dto.YoutubeLiveStreamInsertRequest
import com.capstone.focus.common.external.youtube.dto.YoutubeLiveStreamSnippet
import com.capstone.focus.domain.MemberRepository
import com.capstone.focus.domain.entity.Broadcast
import com.capstone.focus.domain.entity.StreamingPlatformConnection
import com.capstone.focus.domain.entity.enum.StreamingPlatform
import com.capstone.focus.domain.repository.StreamingPlatformConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

interface YoutubePlatformService {
    fun createConnectUrl(memberId: String): YoutubeConnectResponse
    fun handleCallback(code: String, state: String): YoutubeConnectionStatusResponse
    fun getConnectionStatus(memberId: String): YoutubeConnectionStatusResponse
    fun disconnect(memberId: String)
    fun prepareBroadcastTarget(memberId: String, broadcast: Broadcast): YoutubeBroadcastTarget
    fun completeBroadcast(memberId: String, broadcast: Broadcast)
}

data class YoutubeBroadcastTarget(
    val platformChannelId: String,
    val watchUrl: String,
    val outputUrl: String
)

@Service
class YoutubePlatformServiceImpl(
    private val memberRepository: MemberRepository,
    private val redisService: RedisService,
    private val youtubeProperties: YoutubeProperties,
    private val youtubeOAuthClient: YoutubeOAuthClient,
    private val youtubeApiClient: YoutubeApiFeignClient,
    private val connectionRepository: StreamingPlatformConnectionRepository
) : YoutubePlatformService {
    private val logger = LoggerFactory.getLogger(YoutubePlatformServiceImpl::class.java)

    @Transactional(readOnly = true)
    override fun createConnectUrl(memberId: String): YoutubeConnectResponse {
        validateOAuthConfiguration()
        ensureMemberExists(memberId)
        val state = UUID.randomUUID().toString()
        redisService.setValueWithTTL(
            key = stateKey(state),
            value = memberId,
            timeout = youtubeProperties.oauthStateTtlSeconds,
            timeUnit = TimeUnit.SECONDS
        )
        return YoutubeConnectResponse(authUrl = youtubeOAuthClient.getAuthorizationUrl(state))
    }

    @Transactional
    override fun handleCallback(code: String, state: String): YoutubeConnectionStatusResponse {
        validateOAuthConfiguration()
        val memberId = redisService.getValue(stateKey(state))
            ?: throw ApiException(ErrorTitle.BadRequest, "유효하지 않거나 만료된 유튜브 OAuth state 입니다.")

        val member = memberRepository.findByIdOrNull(memberId)
            ?: throw ApiException(ErrorTitle.NotFoundUser)

        val token = try {
            youtubeOAuthClient.issueToken(code)
        } catch (exception: Exception) {
            logger.error("Failed to issue YouTube token. memberId={}", memberId, exception)
            throw ApiException(ErrorTitle.ExternalServerError, "유튜브 토큰 발급에 실패했습니다.")
        }
        val channel = getCurrentChannel(token.accessToken)

        val refreshToken = token.refreshToken
            ?: connectionRepository.findByMember_IdAndPlatform(member.id, StreamingPlatform.YOUTUBE)?.refreshToken
            ?: throw ApiException(ErrorTitle.InvalidToken, "유튜브 refresh token이 응답에 없습니다. 동의 화면에서 다시 연동해 주세요.")

        val connection = connectionRepository.findByMember_IdAndPlatform(member.id, StreamingPlatform.YOUTUBE)
            ?.also {
                it.reconnect(
                    platformUserId = channel.channelId,
                    platformChannelId = channel.channelId,
                    platformChannelName = channel.channelName,
                    accessToken = token.accessToken,
                    refreshToken = refreshToken,
                    accessTokenExpiresAt = LocalDateTime.now().plusSeconds(token.expiresIn)
                )
            }
            ?: StreamingPlatformConnection(
                member = member,
                platform = StreamingPlatform.YOUTUBE,
                platformUserId = channel.channelId,
                platformChannelId = channel.channelId,
                platformChannelName = channel.channelName,
                accessToken = token.accessToken,
                refreshToken = refreshToken,
                accessTokenExpiresAt = LocalDateTime.now().plusSeconds(token.expiresIn)
            )

        connectionRepository.save(connection)
        redisService.deleteKey(stateKey(state))

        return connection.toStatusResponse(buildChannelUrl(connection.platformChannelId))
    }

    @Transactional(readOnly = true)
    override fun getConnectionStatus(memberId: String): YoutubeConnectionStatusResponse {
        ensureMemberExists(memberId)
        val connection = connectionRepository.findByMember_IdAndPlatformAndRevokedAtIsNull(memberId, StreamingPlatform.YOUTUBE)
            ?: return YoutubeConnectionStatusResponse(connected = false)

        return connection.toStatusResponse(buildChannelUrl(connection.platformChannelId))
    }

    @Transactional
    override fun disconnect(memberId: String) {
        val connection = connectionRepository.findByMember_IdAndPlatformAndRevokedAtIsNull(memberId, StreamingPlatform.YOUTUBE)
            ?: return

        youtubeOAuthClient.revokeToken(connection.accessToken)
        connection.revoke()
        connectionRepository.save(connection)
    }

    @Transactional
    override fun prepareBroadcastTarget(memberId: String, broadcast: Broadcast): YoutubeBroadcastTarget {
        validateOAuthConfiguration()
        val connection = getActiveConnection(memberId)
        refreshIfNeeded(connection)

        val title = broadcast.title?.takeIf { it.isNotBlank() } ?: "Focus Live"
        val liveBroadcast = call("createBroadcast") {
            youtubeApiClient.createBroadcast(
                authorization = bearer(connection.accessToken),
                request = YoutubeLiveBroadcastInsertRequest(
                    snippet = YoutubeLiveBroadcastSnippet(
                        title = title,
                        scheduledStartTime = Instant.now().plusSeconds(60).toString()
                    ),
                    status = YoutubeLiveBroadcastStatus(
                        privacyStatus = youtubeProperties.privacyStatus,
                        selfDeclaredMadeForKids = youtubeProperties.madeForKids
                    ),
                    contentDetails = YoutubeLiveBroadcastContentDetails(
                        enableAutoStart = youtubeProperties.enableAutoStart,
                        enableAutoStop = youtubeProperties.enableAutoStop,
                        recordFromStart = youtubeProperties.recordFromStart,
                        latencyPreference = youtubeProperties.latencyPreference
                    )
                )
            )
        }

        val liveStream = call("createStream") {
            youtubeApiClient.createStream(
                authorization = bearer(connection.accessToken),
                request = YoutubeLiveStreamInsertRequest(
                    snippet = YoutubeLiveStreamSnippet(title = "$title stream"),
                    cdn = YoutubeLiveStreamCdn()
                )
            )
        }

        call("bindBroadcast") {
            youtubeApiClient.bindBroadcast(
                authorization = bearer(connection.accessToken),
                id = liveBroadcast.id,
                streamId = liveStream.id
            )
        }

        val ingestion = liveStream.cdn?.ingestionInfo
            ?: throw ApiException(ErrorTitle.ExternalServerError, "유튜브 ingest 정보가 응답에 없습니다.")

        return YoutubeBroadcastTarget(
            platformChannelId = connection.platformChannelId,
            watchUrl = buildWatchUrl(liveBroadcast.id),
            outputUrl = buildPublishUrl(ingestion)
        )
    }

    @Transactional
    override fun completeBroadcast(memberId: String, broadcast: Broadcast) {
        val youtubeBroadcastId = extractYoutubeBroadcastId(broadcast.watchUrl) ?: return
        val connection = getActiveConnection(memberId)
        refreshIfNeeded(connection)

        try {
            call("transitionBroadcastComplete") {
                youtubeApiClient.transitionBroadcast(
                    authorization = bearer(connection.accessToken),
                    id = youtubeBroadcastId,
                    broadcastStatus = "complete"
                )
            }
        } catch (exception: Exception) {
            logger.warn("Failed to complete YouTube live broadcast. broadcastId={}, youtubeBroadcastId={}", broadcast.id, youtubeBroadcastId, exception)
        }
    }

    private fun getCurrentChannel(accessToken: String): CurrentYoutubeChannel {
        val item = call("getCurrentChannel") {
            youtubeApiClient.getMyChannels(bearer(accessToken))
        }.items.orEmpty().firstOrNull()
            ?: throw ApiException(ErrorTitle.BadRequest, "유튜브 채널을 찾을 수 없습니다. 채널 생성 또는 브랜드 계정 선택 상태를 확인해 주세요.")
        return CurrentYoutubeChannel(
            channelId = item.id,
            channelName = item.snippet?.title
        )
    }

    private fun getActiveConnection(memberId: String): StreamingPlatformConnection {
        return connectionRepository.findByMember_IdAndPlatformAndRevokedAtIsNull(memberId, StreamingPlatform.YOUTUBE)
            ?: throw ApiException(ErrorTitle.BadRequest, "유튜브 채널 연동이 필요합니다.")
    }

    private fun refreshIfNeeded(connection: StreamingPlatformConnection) {
        if (!connection.needsRefresh(youtubeProperties.tokenRefreshBufferSeconds)) {
            return
        }

        val refreshedToken = try {
            youtubeOAuthClient.refreshToken(connection.refreshToken)
        } catch (exception: Exception) {
            connection.revoke()
            connectionRepository.save(connection)
            throw ApiException(ErrorTitle.InvalidToken, "유튜브 토큰이 만료되었거나 재발급에 실패했습니다. 다시 연동해 주세요.")
        }

        connection.updateTokens(
            accessToken = refreshedToken.accessToken,
            refreshToken = refreshedToken.refreshToken ?: connection.refreshToken,
            accessTokenExpiresAt = LocalDateTime.now().plusSeconds(refreshedToken.expiresIn)
        )
        connectionRepository.save(connection)
    }

    private fun ensureMemberExists(memberId: String) {
        if (!memberRepository.existsById(memberId)) {
            throw ApiException(ErrorTitle.NotFoundUser)
        }
    }

    private fun validateOAuthConfiguration() {
        if (youtubeProperties.clientId.isBlank() || youtubeProperties.clientSecret.isBlank() || youtubeProperties.redirectUri.isBlank()) {
            throw ApiException(
                ErrorTitle.BadRequest,
                "유튜브 OAuth 설정이 비어 있습니다. clientId, clientSecret, redirectUri 값을 확인해 주세요."
            )
        }
    }

    private fun <T> call(label: String, action: () -> T): T {
        return try {
            action()
        } catch (exception: ApiException) {
            throw exception
        } catch (exception: Exception) {
            logger.error("YouTube API call failed. label={}", label, exception)
            throw ApiException(ErrorTitle.ExternalServerError, "유튜브 API 호출에 실패했습니다. [$label]")
        }
    }

    private fun buildChannelUrl(channelId: String): String = "https://www.youtube.com/channel/$channelId"

    private fun buildWatchUrl(youtubeBroadcastId: String): String {
        return youtubeProperties.watchUrlTemplate.replace("{broadcastId}", youtubeBroadcastId)
    }

    private fun buildPublishUrl(ingestion: YoutubeIngestionInfo): String {
        val baseUrl = if (youtubeProperties.preferRtmps && !ingestion.rtmpsIngestionAddress.isNullOrBlank()) {
            ingestion.rtmpsIngestionAddress
        } else {
            ingestion.ingestionAddress
        }
        return "${baseUrl.trimEnd('/')}/${ingestion.streamName}"
    }

    private fun extractYoutubeBroadcastId(watchUrl: String?): String? {
        if (watchUrl.isNullOrBlank()) {
            return null
        }
        return watchUrl.substringAfter("v=", missingDelimiterValue = "")
            .substringBefore("&")
            .takeIf { it.isNotBlank() }
    }

    private fun bearer(accessToken: String): String = "Bearer $accessToken"

    private fun stateKey(state: String): String = "oauth:state:youtube:$state"

    private fun StreamingPlatformConnection.toStatusResponse(channelUrl: String): YoutubeConnectionStatusResponse {
        return YoutubeConnectionStatusResponse(
            connected = !isRevoked(),
            channelId = platformChannelId,
            channelName = platformChannelName,
            watchUrl = channelUrl,
            accessTokenExpiresAt = accessTokenExpiresAt,
            connectedAt = connectedAt
        )
    }

    private data class CurrentYoutubeChannel(
        val channelId: String,
        val channelName: String?
    )
}
