package com.capstone.focus.common.external.kakao

import com.capstone.focus.auth.dto.kakao.KakaoUserInfoResponse
import com.capstone.focus.common.config.FeignConfig
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader

@FeignClient(
    name = "kakao-api",
    url = "https://kapi.kakao.com",
    configuration = [FeignConfig::class]
)
interface KakaoApiFeignClient {

    @GetMapping(
        value = ["/v2/user/me"],
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE]
    )
    fun getUserInfo(
        @RequestHeader("Authorization") authorization: String
    ): KakaoUserInfoResponse
}