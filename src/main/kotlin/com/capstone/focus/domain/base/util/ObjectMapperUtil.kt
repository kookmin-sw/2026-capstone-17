package com.capstone.focus.domain.base.util

import com.capstone.focus.common.exception.ApiException
import com.capstone.focus.common.exception.ErrorTitle
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * JSON 처리 유틸리티 객체
 */
object ObjectMapperUtil {

    /**
     * 공통 ObjectMapper 인스턴스 (Kotlin 모듈 포함)
     */
    val mapper: ObjectMapper = jacksonObjectMapper()

    /**
     * 객체를 JSON 문자열로 변환
     */
    inline fun <reified T> T.toJsonString(): String =
        mapper.writeValueAsString(this)

    /**
     * JSON 문자열 유효성 검증
     * @throws ApiException JSON 형식이 올바르지 않은 경우
     */
    fun String.validateJson(): String = apply {
        runCatching {
            mapper.readTree(this)
        }.getOrElse {
            throw ApiException(ErrorTitle.InvalidJsonType)
        }
    }

    /**
     * JsonNode를 특정 DTO 클래스로 변환 가능한지 확인
     */
    inline fun <reified T> JsonNode.canConvertTo(): Boolean =
        runCatching {
            mapper.treeToValue(this, T::class.java)
        }.isSuccess

    /**
     * JsonNode를 특정 DTO 클래스로 변환
     * @throws ApiException 변환에 실패한 경우
     */
    inline fun <reified T> JsonNode.convertTo(): T =
        runCatching {
            mapper.treeToValue(this, T::class.java)
        }.getOrElse {
            throw ApiException(ErrorTitle.JsonConvertFail, "JsonNode를 ${T::class.simpleName}로 변환할 수 없습니다.")
        }

    // 하위 호환성을 위한 기존 메서드들
    @Deprecated("toJsonString() 확장 함수 사용을 권장합니다.", ReplaceWith("data.toJsonString()"))
    fun writeValueAsString(data: Any): String = data.toJsonString()

    @Deprecated("validateJson() 확장 함수 사용을 권장합니다.", ReplaceWith("json.validateJson()"))
    fun validateJsonString(json: String): Unit = json.validateJson().let { }

    @Deprecated("canConvertTo<T>() 확장 함수 사용을 권장합니다.")
    fun isJsonNodeStructureMatchingDto(jsonNode: JsonNode, dtoClass: Class<*>): Boolean =
        runCatching {
            mapper.treeToValue(jsonNode, dtoClass)
        }.isSuccess
}