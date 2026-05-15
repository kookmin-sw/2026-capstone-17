package com.capstone.focus.common.external.youtube

import com.capstone.focus.common.config.FeignConfig
import com.capstone.focus.common.external.youtube.dto.YoutubeChannelsResponse
import com.capstone.focus.common.external.youtube.dto.YoutubeLiveBroadcastInsertRequest
import com.capstone.focus.common.external.youtube.dto.YoutubeLiveBroadcastResponse
import com.capstone.focus.common.external.youtube.dto.YoutubeLiveStreamInsertRequest
import com.capstone.focus.common.external.youtube.dto.YoutubeLiveStreamResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
    name = "youtube-live-api",
    url = "\${google.youtube.api-base-url}",
    configuration = [FeignConfig::class]
)
interface YoutubeApiFeignClient {

    @GetMapping("/youtube/v3/channels")
    fun getMyChannels(
        @RequestHeader("Authorization") authorization: String,
        @RequestParam("part") part: String = "id,snippet",
        @RequestParam("mine") mine: Boolean = true
    ): YoutubeChannelsResponse

    @PostMapping("/youtube/v3/liveBroadcasts")
    fun createBroadcast(
        @RequestHeader("Authorization") authorization: String,
        @RequestParam("part") part: String = "snippet,contentDetails,status",
        @RequestBody request: YoutubeLiveBroadcastInsertRequest
    ): YoutubeLiveBroadcastResponse

    @PostMapping("/youtube/v3/liveStreams")
    fun createStream(
        @RequestHeader("Authorization") authorization: String,
        @RequestParam("part") part: String = "snippet,cdn",
        @RequestBody request: YoutubeLiveStreamInsertRequest
    ): YoutubeLiveStreamResponse

    @PostMapping("/youtube/v3/liveBroadcasts/bind")
    fun bindBroadcast(
        @RequestHeader("Authorization") authorization: String,
        @RequestParam("id") id: String,
        @RequestParam("streamId") streamId: String,
        @RequestParam("part") part: String = "id,snippet,contentDetails,status",
        @RequestBody(required = false) emptyBody: String = ""
    ): YoutubeLiveBroadcastResponse

    @PostMapping("/youtube/v3/liveBroadcasts/transition")
    fun transitionBroadcast(
        @RequestHeader("Authorization") authorization: String,
        @RequestParam("id") id: String,
        @RequestParam("broadcastStatus") broadcastStatus: String,
        @RequestParam("part") part: String = "id,snippet,contentDetails,status",
        @RequestBody(required = false) emptyBody: String = ""
    ): YoutubeLiveBroadcastResponse
}
