package com.capstone.focus.common.external.chzzk

import com.capstone.focus.common.config.ChzzkProperties
import com.capstone.focus.common.exception.ApiException
import com.capstone.focus.common.exception.ErrorTitle
import com.capstone.focus.common.external.chzzk.dto.ChzzkApiResponse
import com.capstone.focus.common.external.chzzk.dto.ChzzkLiveContent
import com.capstone.focus.common.external.chzzk.dto.ChzzkLiveSettingPatchRequest
import com.capstone.focus.common.external.chzzk.dto.ChzzkStreamKeyContent
import com.capstone.focus.common.external.chzzk.dto.ChzzkTokenContent
import com.capstone.focus.common.external.chzzk.dto.ChzzkTokenIssueRequest
import com.capstone.focus.common.external.chzzk.dto.ChzzkTokenRefreshRequest
import com.capstone.focus.common.external.chzzk.dto.ChzzkTokenRevokeRequest
import com.capstone.focus.common.external.chzzk.dto.ChzzkUserMeContent
import feign.FeignException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class ChzzkClient(
    private val chzzkProperties: ChzzkProperties,
    private val chzzkOpenApiFeignClient: ChzzkOpenApiFeignClient
) {
    private val logger = LoggerFactory.getLogger(ChzzkClient::class.java)

    data class LiveSnapshot(
        val channelId: String,
        val channelName: String?,
        val liveTitle: String?,
        val concurrentUserCount: Long?,
        val categoryType: String?,
        val liveCategoryId: String?,
        val liveCategoryName: String?
    )

    fun getAuthorizationUrl(state: String): String {
        return UriComponentsBuilder.fromUriString(chzzkProperties.authorizationUri)
            .queryParam("clientId", chzzkProperties.clientId)
            .queryParam("redirectUri", chzzkProperties.redirectUri)
            .queryParam("state", state)
            .build(true)
            .toUriString()
    }

    fun issueToken(code: String, state: String): ChzzkTokenContent {
        return call("issueToken") {
            unwrap(
                chzzkOpenApiFeignClient.issueToken(
                    ChzzkTokenIssueRequest(
                        grantType = "authorization_code",
                        clientId = chzzkProperties.clientId,
                        clientSecret = chzzkProperties.clientSecret,
                        code = code,
                        state = state
                    )
                )
            )
        }
    }

    fun refreshToken(refreshToken: String): ChzzkTokenContent {
        return call("refreshToken") {
            unwrap(
                chzzkOpenApiFeignClient.refreshToken(
                    ChzzkTokenRefreshRequest(
                        grantType = "refresh_token",
                        refreshToken = refreshToken,
                        clientId = chzzkProperties.clientId,
                        clientSecret = chzzkProperties.clientSecret
                    )
                )
            )
        }
    }

    fun revokeToken(token: String, tokenTypeHint: String = "access_token") {
        call("revokeToken") {
            chzzkOpenApiFeignClient.revokeToken(
                ChzzkTokenRevokeRequest(
                    clientId = chzzkProperties.clientId,
                    clientSecret = chzzkProperties.clientSecret,
                    token = token,
                    tokenTypeHint = tokenTypeHint
                )
            )
            Unit
        }
    }

    fun getCurrentUser(accessToken: String): ChzzkUserMeContent {
        return call("getCurrentUser") {
            unwrap(chzzkOpenApiFeignClient.getCurrentUser(bearer(accessToken)))
        }
    }

    fun getLiveSnapshotByChannelId(
        channelId: String,
        pageSize: Int = 20,
        maxPages: Int = 10
    ): LiveSnapshot? {
        return call("getLiveSnapshotByChannelId") {
            var next: String? = null
            var pageCount = 0

            while (pageCount < maxPages) {
                val content = unwrap(
                    chzzkOpenApiFeignClient.getLives(
                        clientId = chzzkProperties.clientId,
                        clientSecret = chzzkProperties.clientSecret,
                        size = pageSize,
                        next = next
                    )
                )

                val live = content.data.firstOrNull { it.channelId == channelId }
                if (live != null) {
                    return@call live.toSnapshot()
                }

                next = content.page?.next?.takeIf { it.isNotBlank() }
                if (next == null) {
                    break
                }
                pageCount++
            }

            null
        }
    }

    fun getStreamKey(accessToken: String): ChzzkStreamKeyContent {
        return call("getStreamKey") {
            unwrap(chzzkOpenApiFeignClient.getStreamKey(bearer(accessToken)))
        }
    }

    fun updateLiveTitle(accessToken: String, title: String) {
        call("updateLiveTitle") {
            chzzkOpenApiFeignClient.updateLiveSetting(
                authorization = bearer(accessToken),
                request = ChzzkLiveSettingPatchRequest(defaultLiveTitle = title)
            )
            Unit
        }
    }

    private fun <T> unwrap(response: ChzzkApiResponse<T>): T {
        if (response.code !in 200..299) {
            throw ApiException(ErrorTitle.ExternalServerError, response.message ?: "치지직 API 호출에 실패했습니다.")
        }
        return response.content
            ?: throw ApiException(ErrorTitle.ExternalServerError, "치지직 API 응답 본문이 비어 있습니다.")
    }

    private fun bearer(accessToken: String): String = "Bearer $accessToken"

    private fun <T> call(label: String, action: () -> T): T {
        return try {
            action()
        } catch (exception: ApiException) {
            throw exception
        } catch (exception: FeignException) {
            logger.error("CHZZK API call failed. label={}, status={}, message={}", label, exception.status(), exception.message)
            throw ApiException(ErrorTitle.ExternalServerError, "치지직 API 호출에 실패했습니다. [$label]")
        } catch (exception: Exception) {
            logger.error("CHZZK API call failed. label={}", label, exception)
            throw ApiException(ErrorTitle.ExternalServerError, "치지직 API 호출에 실패했습니다. [$label]")
        }
    }

    private fun ChzzkLiveContent.toSnapshot(): LiveSnapshot {
        return LiveSnapshot(
            channelId = channelId,
            channelName = channelName,
            liveTitle = liveTitle,
            concurrentUserCount = concurrentUserCount,
            categoryType = categoryType,
            liveCategoryId = liveCategory,
            liveCategoryName = liveCategoryValue
        )
    }
}
