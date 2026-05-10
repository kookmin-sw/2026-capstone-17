package com.kmu_focus.focusandroid.feature.account.data.remote.dto

import com.kmu_focus.focusandroid.feature.account.domain.entity.UserProfile

data class UserInfoResponseDto(
    val id: String,
    val kakaoId: Long,
    val name: String,
    val email: String? = null,
    val profileImageUrl: String? = null,
)

fun UserInfoResponseDto.toEntity(): UserProfile {
    return UserProfile(
        id = id,
        name = name,
        email = email,
        profileImageUrl = profileImageUrl,
    )
}
