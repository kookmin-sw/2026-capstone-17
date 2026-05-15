package com.capstone.focus.common.external.chzzk

import com.capstone.focus.common.config.FeignConfig
import com.capstone.focus.common.external.chzzk.dto.ChzzkApiResponse
import com.capstone.focus.common.external.chzzk.dto.ChzzkLiveListContent
import com.capstone.focus.common.external.chzzk.dto.ChzzkLiveSettingPatchRequest
import com.capstone.focus.common.external.chzzk.dto.ChzzkStreamKeyContent
import com.capstone.focus.common.external.chzzk.dto.ChzzkTokenContent
import com.capstone.focus.common.external.chzzk.dto.ChzzkTokenIssueRequest
import com.capstone.focus.common.external.chzzk.dto.ChzzkTokenRefreshRequest
import com.capstone.focus.common.external.chzzk.dto.ChzzkTokenRevokeRequest
import com.capstone.focus.common.external.chzzk.dto.ChzzkUserMeContent
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
    name = "chzzk-open-api",
    url = "\${naver.chzzk.open-api-base-url}",
    configuration = [FeignConfig::class]
)
interface ChzzkOpenApiFeignClient {

    @GetMapping("/open/v1/lives")
    fun getLives(
        @RequestHeader("Client-Id") clientId: String,
        @RequestHeader("Client-Secret") clientSecret: String,
        @RequestParam("size") size: Int? = null,
        @RequestParam("next") next: String? = null
    ): ChzzkApiResponse<ChzzkLiveListContent>

    @PostMapping("/auth/v1/token")
    fun issueToken(@RequestBody request: ChzzkTokenIssueRequest): ChzzkApiResponse<ChzzkTokenContent>

    @PostMapping("/auth/v1/token")
    fun refreshToken(@RequestBody request: ChzzkTokenRefreshRequest): ChzzkApiResponse<ChzzkTokenContent>

    @PostMapping("/auth/v1/token/revoke")
    fun revokeToken(@RequestBody request: ChzzkTokenRevokeRequest): ChzzkApiResponse<Map<String, Any>?>

    @GetMapping("/open/v1/users/me")
    fun getCurrentUser(
        @RequestHeader("Authorization") authorization: String
    ): ChzzkApiResponse<ChzzkUserMeContent>

    @GetMapping("/open/v1/streams/key")
    fun getStreamKey(
        @RequestHeader("Authorization") authorization: String
    ): ChzzkApiResponse<ChzzkStreamKeyContent>

    @PatchMapping("/open/v1/lives/setting")
    fun updateLiveSetting(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: ChzzkLiveSettingPatchRequest
    ): ChzzkApiResponse<Map<String, Any>?>
}
