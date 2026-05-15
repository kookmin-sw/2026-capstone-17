package com.capstone.focus.common.external.youtube

import com.capstone.focus.common.config.YoutubeProperties
import com.capstone.focus.common.external.youtube.dto.YoutubeTokenResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.http.MediaType

@Component
class YoutubeOAuthClient(
    private val youtubeProperties: YoutubeProperties
) {
    private val logger = LoggerFactory.getLogger(YoutubeOAuthClient::class.java)
    private val restClient: RestClient = RestClient.builder().build()

    fun getAuthorizationUrl(state: String): String {
        return UriComponentsBuilder.fromUriString(youtubeProperties.authorizationUri)
            .queryParam("client_id", youtubeProperties.clientId)
            .queryParam("redirect_uri", youtubeProperties.redirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", youtubeProperties.scope)
            .queryParam("state", state)
            .queryParam("access_type", "offline")
            .queryParam("prompt", "consent")
            .build()
            .toUriString()
    }

    fun issueToken(code: String): YoutubeTokenResponse {
        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("code", code)
            add("client_id", youtubeProperties.clientId)
            add("client_secret", youtubeProperties.clientSecret)
            add("redirect_uri", youtubeProperties.redirectUri)
        }
        return requestToken(body)
    }

    fun refreshToken(refreshToken: String): YoutubeTokenResponse {
        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "refresh_token")
            add("refresh_token", refreshToken)
            add("client_id", youtubeProperties.clientId)
            add("client_secret", youtubeProperties.clientSecret)
        }
        return requestToken(body)
    }

    fun revokeToken(token: String) {
        try {
            val body = LinkedMultiValueMap<String, String>().apply {
                add("token", token)
            }
            restClient.post()
                .uri(youtubeProperties.revokeUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .toBodilessEntity()
        } catch (exception: Exception) {
            logger.warn("Failed to revoke YouTube token.", exception)
        }
    }

    private fun requestToken(body: LinkedMultiValueMap<String, String>): YoutubeTokenResponse {
        return restClient.post()
            .uri(youtubeProperties.tokenUri)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(body)
            .retrieve()
            .body(YoutubeTokenResponse::class.java)
            ?: throw IllegalStateException("YouTube token response body is empty.")
    }
}
