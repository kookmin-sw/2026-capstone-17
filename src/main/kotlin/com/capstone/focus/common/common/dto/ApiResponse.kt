package com.capstone.focus.common.common.dto

import com.capstone.focus.common.exception.ErrorTitle
import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "API 응답")
sealed class ApiResponse<out T> {
    
    @Schema(description = "성공 응답")
    data class Success<T>(
        @Schema(description = "성공 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        val success: Boolean = true,
        
        @Schema(description = "응답 메시지", example = "요청이 성공적으로 처리되었습니다.", requiredMode = Schema.RequiredMode.REQUIRED)
        val message: String,
        
        @Schema(description = "응답 데이터", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val data: T? = null
    ) : ApiResponse<T>()
    
    @Schema(description = "실패 응답")
    data class Failure(
        @Schema(description = "성공 여부", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        val success: Boolean = false,
        
        @Schema(description = "에러 메시지", example = "요청 처리 중 오류가 발생했습니다.", requiredMode = Schema.RequiredMode.REQUIRED)
        val message: String,
        
        @Schema(description = "에러 타이틀", example = "BadRequest", requiredMode = Schema.RequiredMode.REQUIRED)
        val errorTitle: String,
        
        @Schema(description = "에러 코드", example = "400", requiredMode = Schema.RequiredMode.REQUIRED)
        val errorCode: Int
    ) : ApiResponse<Nothing>()
}

// 편의성을 위한 팩토리 함수들
object ApiResponseFactory {
    
    fun <T> success(message: String, data: T? = null): ApiResponse.Success<T> =
        ApiResponse.Success(message = message, data = data)
    
    fun success(message: String): ApiResponse.Success<Unit> =
        ApiResponse.Success(message = message, data = null)
    
    fun failure(errorTitle: ErrorTitle, message: String? = null): ApiResponse.Failure =
        ApiResponse.Failure(
            message = message ?: errorTitle.message,
            errorTitle = errorTitle.name,
            errorCode = errorTitle.status.value()
        )
} 