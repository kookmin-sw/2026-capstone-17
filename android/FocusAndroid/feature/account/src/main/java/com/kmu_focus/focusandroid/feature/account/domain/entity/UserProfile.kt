package com.kmu_focus.focusandroid.feature.account.domain.entity

data class UserProfile(
    val id: String,
    val name: String,
    val email: String? = null,
    val profileImageUrl: String? = null,
)
