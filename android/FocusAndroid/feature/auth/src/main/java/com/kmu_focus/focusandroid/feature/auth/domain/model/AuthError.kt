package com.kmu_focus.focusandroid.feature.auth.domain.model

sealed class AuthError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    object InvalidContext : AuthError("유효한 Context가 필요합니다")
    object UserCancelled : AuthError("로그인 취소")
    object TokenExpired : AuthError("토큰이 만료되었습니다")
    object TokenMissing : AuthError("토큰이 없습니다")

    class Network(
        message: String = "네트워크 오류",
        cause: Throwable? = null,
    ) : AuthError(message, cause)

    class Unexpected(
        message: String = "로그인 실패",
        cause: Throwable? = null,
    ) : AuthError(message, cause)
}
