package com.capstone.focus.auth.dto.kakao

import com.fasterxml.jackson.annotation.JsonProperty

data class KakaoUserInfoResponse(
    val id: Long,

    @JsonProperty("connected_at")
    val connectedAt: String? = null,

    @JsonProperty("kakao_account")
    val kakaoAccount: KakaoAccount? = null,

    val properties: Properties? = null
) {
    data class KakaoAccount(
        @JsonProperty("profile_nickname_needs_agreement")
        val profileNicknameNeedsAgreement: Boolean? = null,

        @JsonProperty("profile_image_needs_agreement")
        val profileImageNeedsAgreement: Boolean? = null,

        val profile: Profile? = null,

        @JsonProperty("has_email")
        val hasEmail: Boolean? = null,

        @JsonProperty("email_needs_agreement")
        val emailNeedsAgreement: Boolean? = null,

        @JsonProperty("is_email_valid")
        val isEmailValid: Boolean? = null,

        @JsonProperty("is_email_verified")
        val isEmailVerified: Boolean? = null,

        val email: String? = null
    )

    data class Profile(
        val nickname: String? = null,

        @JsonProperty("thumbnail_image_url")
        val thumbnailImageUrl: String? = null,

        @JsonProperty("profile_image_url")
        val profileImageUrl: String? = null,

        @JsonProperty("is_default_image")
        val isDefaultImage: Boolean? = null
    )

    data class Properties(
        val nickname: String? = null,

        @JsonProperty("profile_image")
        val profileImage: String? = null,

        @JsonProperty("thumbnail_image")
        val thumbnailImage: String? = null
    )
}