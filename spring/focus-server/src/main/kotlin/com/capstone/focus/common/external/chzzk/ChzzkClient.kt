package com.capstone.focus.common.external.chzzk

import com.capstone.focus.common.config.ChzzkProperties
import com.capstone.focus.common.exception.ApiException
import com.capstone.focus.common.exception.ErrorTitle
import com.capstone.focus.common.external.chzzk.dto.ChzzkApiResponse
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
}
