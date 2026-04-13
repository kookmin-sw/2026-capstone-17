package com.capstone.focus.common.exception

/**
 * API 예외 클래스
 * @param errorTitle 에러 타이틀 (HTTP 상태코드와 기본 메시지 포함)
 * @param message 커스텀 에러 메시지 (null인 경우 errorTitle의 기본 메시지 사용)
 */
class ApiException(
    val errorTitle: ErrorTitle,
    override val message: String = errorTitle.message
) : RuntimeException(message) {
    
    /**
     * ErrorTitle만으로 생성하는 편의 생성자
     */
    constructor(errorTitle: ErrorTitle) : this(errorTitle, errorTitle.message)
}