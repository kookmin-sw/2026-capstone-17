package com.kmu_focus.focusandroid.core.streaming.domain.entity

data class SrtConnectionConfig(
    val host: String,
    val port: Int,
    val streamKey: String,
) {
    val streamId: String
        get() = "publish:live/$streamKey"
}
