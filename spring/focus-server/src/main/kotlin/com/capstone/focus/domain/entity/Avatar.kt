package com.capstone.focus.domain.entity

import com.capstone.focus.domain.base.UlidPrimaryKeyEntity
import com.capstone.focus.domain.entity.enum.AvatarAgeGroup
import com.capstone.focus.domain.entity.enum.AvatarEthnicity
import com.capstone.focus.domain.entity.enum.AvatarGender
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "avatar")
@AttributeOverride(name = "id", column = Column(name = "avatar_id"))
class Avatar(
    avatarName: String?,
    ObjectKey: String,
    thumbnailUrl: String? = null,
    gender: AvatarGender? = null,
    ageGroup: AvatarAgeGroup? = null,
    ethnicity: AvatarEthnicity? = null,
) : UlidPrimaryKeyEntity() {

    @Column(name = "avatar_name")
    var avatarName: String? = avatarName
        protected set

    @Column(name = "object_key", nullable = false)
    var ObjectKey: String = ObjectKey
        protected set

    @Column(name = "thumbnail_url")
    var thumbnailUrl: String? = thumbnailUrl
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    var gender: AvatarGender? = gender
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "age_group", length = 20)
    var ageGroup: AvatarAgeGroup? = ageGroup
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "ethnicity", length = 20)
    var ethnicity: AvatarEthnicity? = ethnicity
        protected set

    @Column(name = "is_active")
    var isActive: Boolean = true
        protected set

    fun updateMetadata(
        gender: AvatarGender?,
        ageGroup: AvatarAgeGroup?,
        ethnicity: AvatarEthnicity?
    ) {
        this.gender = gender
        this.ageGroup = ageGroup
        this.ethnicity = ethnicity
    }

    fun deactivate() {
        this.isActive = false
    }
}