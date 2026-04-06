package com.capstone.focus.api.image.dto.response

import com.capstone.focus.domain.entity.MemberImage
import java.time.LocalDateTime

data class MemberImageResponse(
    val imageId: String,
    val memberId: String,
    val imageUrl: String,
    val objectKey: String,
    val originalFilename: String?,
    val contentType: String?,
    val sizeBytes: Long,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(memberImage: MemberImage): MemberImageResponse {
            return MemberImageResponse(
                imageId = memberImage.id,
                memberId = memberImage.member.id,
                imageUrl = memberImage.imageUrl,
                objectKey = memberImage.objectKey,
                originalFilename = memberImage.originalFilename,
                contentType = memberImage.contentType,
                sizeBytes = memberImage.sizeBytes,
                createdAt = memberImage.createdAt
            )
        }
    }
}
