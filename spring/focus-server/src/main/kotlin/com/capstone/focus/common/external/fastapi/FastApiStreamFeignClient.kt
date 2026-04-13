package com.capstone.focus.common.external.fastapi

import com.capstone.focus.common.config.FeignConfig
import com.capstone.focus.common.external.fastapi.dto.FastApiStartStreamRequest
import com.capstone.focus.common.external.fastapi.dto.FastApiStopStreamRequest
import com.capstone.focus.common.external.fastapi.dto.FastApiStreamResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(
    name = "fastapi-stream",
    url = "\${focus.fastapi.base-url}",
    configuration = [FeignConfig::class]
)
interface FastApiStreamFeignClient {

    @PostMapping("/api/stream/start")
    fun startStream(@RequestBody request: FastApiStartStreamRequest): FastApiStreamResponse

    @PostMapping("/api/stream/stop")
    fun stopStream(@RequestBody request: FastApiStopStreamRequest): FastApiStreamResponse

    @GetMapping("/api/stream/{broadcastId}/status")
    fun getStatus(@PathVariable broadcastId: String): FastApiStreamResponse
}
