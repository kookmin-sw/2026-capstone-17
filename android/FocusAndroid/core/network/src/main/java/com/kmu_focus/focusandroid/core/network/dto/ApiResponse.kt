package com.kmu_focus.focusandroid.core.network.dto

data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T?,
)
