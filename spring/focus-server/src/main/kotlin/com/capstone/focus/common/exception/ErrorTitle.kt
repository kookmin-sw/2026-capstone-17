package com.capstone.focus.common.exception

import org.springframework.http.HttpStatus

/**
 * API 에러 타이틀 열거형
 * @param status HTTP 상태 코드
 * @param message 기본 에러 메시지
 */
enum class ErrorTitle(val status: HttpStatus, val message: String) {

    // 400 Bad Request
    ExternalServerError(HttpStatus.BAD_REQUEST, "외부 서버와 통신 과정 중 에러가 발생했습니다."),
    InvalidInputValue(HttpStatus.BAD_REQUEST, "잘못된 Request 형식 입니다."),
    InvalidEnumValue(HttpStatus.BAD_REQUEST, "잘못된 Enum Value 입니다."),
    BadRequest(HttpStatus.BAD_REQUEST, "잘못된 요청 입니다."),
    ModelValidationFail(HttpStatus.BAD_REQUEST, "모델 유효성 검사에 실패했습니다."),
    JsonConvertFail(HttpStatus.BAD_REQUEST, "Json 변환에 실패했습니다."),
    InvalidJsonType(HttpStatus.BAD_REQUEST, "잘못된 Json 형식 입니다."),
    NotSupportedType(HttpStatus.BAD_REQUEST, "지원하지 않는 타입입니다."),
    InvalidQueryParameter(HttpStatus.BAD_REQUEST, "잘못된 Query Parameter 입니다."),
    InvalidImageFile(HttpStatus.BAD_REQUEST, "잘못된 이미지 파일입니다."),

    // 401 Unauthorized
    LoginRequired(HttpStatus.UNAUTHORIZED, "인증되지 않은 사용자입니다."),
    InvalidToken(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    ExpiredToken(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    Unauthorized(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다."),

    // 403 Forbidden
    Forbidden(HttpStatus.FORBIDDEN, "권한이 없습니다."),

    // 404 Not Found
    NotFoundEndpoint(HttpStatus.NOT_FOUND, "존재 하지 않는 엔드포인트 입니다."),
    NotFoundUser(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    NotFoundImage(HttpStatus.NOT_FOUND, "존재하지 않는 이미지 파일입니다."),
    NotFoundBroadcast(HttpStatus.NOT_FOUND, "존재하지 않는 방송입니다."),

    // 405 Method Not Allowed
    MethodNotAllowed(HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 메소드입니다."),

    // 500 Internal Server Error
    InternalServerError(HttpStatus.INTERNAL_SERVER_ERROR, "서버 에러가 발생했습니다."),
    FeignClientError(HttpStatus.INTERNAL_SERVER_ERROR, "외부 API 통신 과정에서 에러 발생"),
    FileUploadFail(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다.");

    /**
     * 에러 타이틀의 이름을 반환 (enum의 name과 동일)
     */
    val errorName: String get() = this.name

    /**
     * 스웨거 문서화를 위한 이름 반환 메서드
     */
    fun getName(): String = this.name
}
