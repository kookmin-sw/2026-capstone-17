package com.capstone.focus.api.image.service

import com.capstone.focus.api.image.dto.response.MemberImageResponse
import com.capstone.focus.common.exception.ApiException
import com.capstone.focus.common.exception.ErrorTitle
import com.capstone.focus.common.external.s3.ImageStorageService
import com.capstone.focus.domain.MemberService
import com.capstone.focus.domain.entity.MemberImage
import com.capstone.focus.domain.repository.MemberImageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

interface MemberImageService {
    fun uploadMemberImage(memberId: String, image: MultipartFile): MemberImageResponse
    fun getMemberImages(memberId: String): List<MemberImageResponse>
    fun deleteMemberImage(memberId: String, imageId: String)
}

@Service
class MemberImageServiceImpl(
    private val memberService: MemberService,
    private val memberImageRepository: MemberImageRepository,
    private val imageStorageService: ImageStorageService
) : MemberImageService {

    @Transactional
    override fun uploadMemberImage(memberId: String, image: MultipartFile): MemberImageResponse {
        val member = memberService.getMemberById(memberId)
        val uploadedImage = imageStorageService.uploadMemberImage(memberId, image)

        val savedImage = memberImageRepository.save(
            MemberImage(
                member = member,
                imageUrl = uploadedImage.imageUrl,
                objectKey = uploadedImage.objectKey,
                originalFilename = uploadedImage.originalFilename,
                contentType = uploadedImage.contentType,
                sizeBytes = uploadedImage.sizeBytes
            )
        )

        return MemberImageResponse.from(savedImage)
    }

    @Transactional(readOnly = true)
    override fun getMemberImages(memberId: String): List<MemberImageResponse> {
        memberService.getMemberById(memberId)

        return memberImageRepository.findAllByMember_IdOrderByCreatedAtDesc(memberId)
            .map(MemberImageResponse::from)
    }

    @Transactional
    override fun deleteMemberImage(memberId: String, imageId: String) {
        val memberImage = memberImageRepository.findByIdAndMember_Id(imageId, memberId)
            ?: throw ApiException(ErrorTitle.NotFoundImage)

        imageStorageService.deleteMemberImage(memberImage.objectKey)
        memberImageRepository.delete(memberImage)
    }
}
