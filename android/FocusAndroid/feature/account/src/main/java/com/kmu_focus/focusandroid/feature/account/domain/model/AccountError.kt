package com.kmu_focus.focusandroid.feature.account.domain.model

sealed class AccountError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Configuration(
        message: String,
        cause: Throwable? = null,
    ) : AccountError(message, cause)

    class Network(
        message: String,
        cause: Throwable? = null,
    ) : AccountError(message, cause)

    class Unexpected(
        message: String,
        cause: Throwable? = null,
    ) : AccountError(message, cause)
}
