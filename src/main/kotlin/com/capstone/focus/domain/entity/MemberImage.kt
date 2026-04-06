package com.capstone.focus.domain.entity

import com.capstone.focus.domain.base.UlidPrimaryKeyEntity
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(
    name = "member_image",
    indexes = [
        Index(name = "idx_member_image_member_id", columnList = "member_id")
    ]
)
@AttributeOverride(name = "id", column = Column(name = "image_id"))
class MemberImage(
    member: Member,
    imageUrl: String,
    objectKey: String,
    originalFilename: String?,
    contentType: String?,
    sizeBytes: Long
) : UlidPrimaryKeyEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    var member: Member = member
        protected set

    @Column(name = "image_url", nullable = false, length = 255)
    var imageUrl: String = imageUrl
        protected set

    @Column(name = "object_key", nullable = false, unique = true, length = 255)
    var objectKey: String = objectKey
        protected set

    @Column(name = "original_filename", length = 255)
    var originalFilename: String? = originalFilename
        protected set

    @Column(name = "content_type", length = 100)
    var contentType: String? = contentType
        protected set

    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Long = sizeBytes
        protected set
}
