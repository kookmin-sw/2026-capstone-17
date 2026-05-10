package com.kmu_focus.focusandroid.feature.account.data.oauth

import com.kmu_focus.focusandroid.feature.account.domain.model.AccountError
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChzzkOAuthConnectUrlValidator @Inject constructor(
    private val config: ChzzkOAuthConfig,
) {

    fun validate(connectUrl: String): Result<String> {
        if (connectUrl.isBlank()) {
            return Result.failure(AccountError.Configuration("치지직 OAuth URL이 비어 있습니다."))
        }

        val parsedUrl = runCatching { URI(connectUrl) }
            .getOrElse {
                return Result.failure(
                    AccountError.Configuration("치지직 OAuth URL 형식이 올바르지 않습니다.", it),
                )
            }

        if (!parsedUrl.isAbsolute || parsedUrl.scheme != "https") {
            return Result.failure(
                AccountError.Configuration("치지직 OAuth URL은 https 절대경로여야 합니다."),
            )
        }

        val expectedBase = config.authBaseUrl.trim()
        if (expectedBase.isNotBlank()) {
            val parsedBase = runCatching { URI(expectedBase) }
                .getOrElse {
                    return Result.failure(
                        AccountError.Configuration("local.properties의 chzzkAuthBaseUrl 설정이 올바르지 않습니다.", it),
                    )
                }

            if (
                parsedUrl.scheme != parsedBase.scheme ||
                parsedUrl.host != parsedBase.host ||
                normalizePath(parsedUrl.path) != normalizePath(parsedBase.path)
            ) {
                return Result.failure(
                    AccountError.Configuration("치지직 OAuth URL이 등록된 authorize endpoint와 일치하지 않습니다."),
                )
            }
        }

        val query = parseQuery(parsedUrl.rawQuery)
        val state = query["state"].orEmpty()
        if (state.isBlank()) {
            return Result.failure(
                AccountError.Configuration("치지직 OAuth URL에 state 값이 없습니다."),
            )
        }

        val expectedClientId = config.clientId.trim()
        if (expectedClientId.isNotBlank() && query["clientId"] != expectedClientId) {
            return Result.failure(
                AccountError.Configuration("치지직 OAuth URL의 clientId가 local.properties 설정과 다릅니다."),
            )
        }

        val expectedRedirectUri = config.redirectUri.trim()
        if (expectedRedirectUri.isNotBlank() && query["redirectUri"] != expectedRedirectUri) {
            return Result.failure(
                AccountError.Configuration("치지직 OAuth URL의 redirectUri가 local.properties 설정과 다릅니다."),
            )
        }

        return Result.success(connectUrl)
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) {
            return emptyMap()
        }

        return rawQuery.split('&')
            .mapNotNull { part ->
                val separatorIndex = part.indexOf('=')
                if (separatorIndex <= 0) {
                    return@mapNotNull null
                }

                val key = decode(part.substring(0, separatorIndex))
                val value = decode(part.substring(separatorIndex + 1))
                key to value
            }
            .toMap()
    }

    private fun decode(value: String): String {
        return URLDecoder.decode(value, StandardCharsets.UTF_8)
    }

    private fun normalizePath(path: String?): String {
        return path.orEmpty().trimEnd('/').ifEmpty { "/" }
    }
}
