package com.capstone.focus.domain.entity

import com.capstone.focus.domain.base.UlidPrimaryKeyEntity
import com.capstone.focus.domain.entity.enum.MemberRole
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.springframework.data.annotation.LastModifiedDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "member",
    indexes = [
        Index(name = "idx_member_email", columnList = "email"),
        Index(name = "idx_member_nickname", columnList = "nickname")
    ])
@AttributeOverride(name = "id", column = Column(name = "member_id"))
class Member(
    email: String,
    nickname: String,
    role: MemberRole = MemberRole.USER,
    faceEmbedding: String? = null
) : UlidPrimaryKeyEntity() {

    @Column(name = "email", nullable = false, unique = true)
    var email: String = email
        protected set

    @Column(name = "nickname", nullable = false)
    var nickname: String = nickname
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20)
    var role: MemberRole = role
        protected set

    @Column(name = "face_embedding", columnDefinition = "jsonb")
    var faceEmbedding: String? = faceEmbedding
        protected set

    @LastModifiedDate
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null
        protected set

    fun updateProfile(nickname: String, faceEmbedding: String?) {
        this.nickname = nickname
        this.faceEmbedding = faceEmbedding
    }
}