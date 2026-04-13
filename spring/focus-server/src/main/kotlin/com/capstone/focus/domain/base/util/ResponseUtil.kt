package com.capstone.focus.domain.base.util

import com.capstone.focus.common.common.dto.ApiResponse
import com.capstone.focus.common.common.dto.ApiResponseFactory
import com.capstone.focus.common.exception.ApiException
import com.capstone.focus.common.exception.ErrorTitle
import org.springframework.http.ResponseEntity

/**
 * API 응답 유틸리티 객체
 */
object ResponseUtil {

    /**
     * 성공 응답 생성 (데이터 포함)
     */
    inline fun <reified T> success(message: String, data: T): ResponseEntity<ApiResponse.Success<T>> =
        ResponseEntity.ok(ApiResponseFactory.success(message, data))

    /**
     * 성공 응답 생성 (데이터 없음)
     */
    fun success(message: String): ResponseEntity<ApiResponse.Success<Unit>> =
        ResponseEntity.ok(ApiResponseFactory.success(message))

    /**
     * 실패 응답 생성 (ApiException 기반)
     */
    fun failure(exception: ApiException): ResponseEntity<ApiResponse.Failure> =
        ResponseEntity
            .status(exception.errorTitle.status)
            .body(ApiResponseFactory.failure(exception.errorTitle, exception.message))

    /**
     * 실패 응답 생성 (ErrorTitle 기반)
     */
    fun failure(errorTitle: ErrorTitle, message: String? = null): ResponseEntity<ApiResponse.Failure> =
        ResponseEntity
            .status(errorTitle.status)
            .body(ApiResponseFactory.failure(errorTitle, message))
}

/**
 * ApiException을 ResponseEntity로 변환하는 확장 함수
 */
fun ApiException.toResponseEntity(): ResponseEntity<ApiResponse.Failure> =
    ResponseUtil.failure(this)

/**
 * ErrorTitle을 ResponseEntity로 변환하는 확장 함수
 */
fun ErrorTitle.toResponseEntity(message: String? = null): ResponseEntity<ApiResponse.Failure> =
    ResponseUtil.failure(this, message)