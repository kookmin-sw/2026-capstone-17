package com.capstone.focus.common.external.fastapi

import com.capstone.focus.common.exception.ApiException
import com.capstone.focus.common.exception.ErrorTitle
import com.capstone.focus.common.external.fastapi.dto.FastApiStartStreamRequest
import com.capstone.focus.common.external.fastapi.dto.FastApiStopStreamRequest
import com.capstone.focus.common.external.fastapi.dto.FastApiStreamResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class FastApiStreamClient(
    private val fastApiStreamFeignClient: FastApiStreamFeignClient
) {
    private val logger = LoggerFactory.getLogger(FastApiStreamClient::class.java)

    fun startBroadcast(
        broadcastId: String,
        streamKey: String,
        avatarId: String?
    ): FastApiStreamResponse {
        return try {
            fastApiStreamFeignClient.startStream(
                FastApiStartStreamRequest(
                    broadcastId = broadcastId,
                    streamKey = streamKey,
                    avatarId = avatarId
                )
            )
        } catch (exception: IllegalArgumentException) {
            throw ApiException(ErrorTitle.BadRequest, exception.message ?: ErrorTitle.BadRequest.message)
        } catch (exception: Exception) {
            logger.error("FastAPI start call failed. broadcastId={}, streamKey={}", broadcastId, streamKey, exception)
            throw ApiException(ErrorTitle.FeignClientError, "FastAPI 방송 시작 호출에 실패했습니다.")
        }
    }

    fun stopBroadcast(broadcastId: String) {
        try {
            fastApiStreamFeignClient.stopStream(FastApiStopStreamRequest(broadcastId = broadcastId))
        } catch (exception: NoSuchElementException) {
            logger.warn("FastAPI worker already missing. broadcastId={}", broadcastId)
        } catch (exception: Exception) {
            logger.error("FastAPI stop call failed. broadcastId={}", broadcastId, exception)
            throw ApiException(ErrorTitle.FeignClientError, "FastAPI 방송 종료 호출에 실패했습니다.")
        }
    }
}
