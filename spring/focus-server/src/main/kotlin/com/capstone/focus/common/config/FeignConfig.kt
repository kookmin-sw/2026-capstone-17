package com.capstone.focus.common.config

import feign.Logger
import feign.codec.ErrorDecoder
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FeignConfig {

    @Bean
    fun feignLoggerLevel(): Logger.Level {
        return Logger.Level.BASIC
    }

    @Bean
    fun errorDecoder(): ErrorDecoder {
        return CustomErrorDecoder()
    }

    class CustomErrorDecoder : ErrorDecoder {
        private val logger = LoggerFactory.getLogger(CustomErrorDecoder::class.java)

        override fun decode(methodKey: String, response: feign.Response): Exception {
            logger.error("Feign client error - Method: $methodKey, Status: ${response.status()}, Reason: ${response.reason()}")

            return when (response.status()) {
                400 -> IllegalArgumentException("잘못된 요청: ${response.reason()}")
                401 -> SecurityException("인증 실패: ${response.reason()}")
                403 -> SecurityException("권한 없음: ${response.reason()}")
                404 -> NoSuchElementException("리소스를 찾을 수 없음: ${response.reason()}")
                500 -> RuntimeException("서버 오류: ${response.reason()}")
                else -> RuntimeException("외부 API 호출 실패: ${response.reason()}")
            }
        }
    }
}