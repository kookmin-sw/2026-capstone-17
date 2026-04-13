package com.capstone.focus.domain.repository

import com.capstone.focus.domain.entity.MemberImage
import org.springframework.data.jpa.repository.JpaRepository

interface MemberImageRepository : JpaRepository<MemberImage, String> {
    fun findAllByMember_IdOrderByCreatedAtDesc(memberId: String): List<MemberImage>
    fun findByIdAndMember_Id(imageId: String, memberId: String): MemberImage?
}
