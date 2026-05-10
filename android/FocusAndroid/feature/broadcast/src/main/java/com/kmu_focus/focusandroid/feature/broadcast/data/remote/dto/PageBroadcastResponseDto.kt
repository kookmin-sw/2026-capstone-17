package com.kmu_focus.focusandroid.feature.broadcast.data.remote.dto

data class PageBroadcastResponseDto(
    val content: List<BroadcastResponseDto>,
    val totalElements: Long,
    val totalPages: Int,
    val size: Int,
    val number: Int,
    val first: Boolean,
    val last: Boolean,
    val empty: Boolean,
)
