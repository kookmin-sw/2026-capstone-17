package com.capstone.focus.api.image.controller

import com.capstone.focus.api.image.dto.response.MemberImageResponse
import com.capstone.focus.api.image.service.MemberImageService
import com.capstone.focus.auth.security.service.FocusMemberDetails
import com.capstone.focus.common.common.annotations.FocusDeleteMapping
import com.capstone.focus.common.common.annotations.FocusGetMapping
import com.capstone.focus.common.common.annotations.FocusPostMapping
import com.capstone.focus.common.common.dto.ApiResponse
import com.capstone.focus.domain.base.util.ResponseUtil
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/members/images")
@Tag(name = "Member Image API", description = "Upload reference images for streamer exclusion.")
@SecurityRequirement(name = "bearerAuth")
class MemberImageController(
    private val memberImageService: MemberImageService
) {

    @FocusGetMapping(authenticated = true)
    @SwaggerApiResponse(responseCode = "200", description = "Image list retrieval success")
    @Operation(
        summary = "Get member images",
        description = "Returns the uploaded reference images for the authenticated member."
    )
    fun getMemberImages(
        @AuthenticationPrincipal memberDetails: FocusMemberDetails
    ): ResponseEntity<ApiResponse.Success<List<MemberImageResponse>>> {
        val response = memberImageService.getMemberImages(memberDetails.getMemberId())
        return ResponseUtil.success("Image list retrieved successfully.", response)
    }

    @FocusPostMapping(authenticated = true, consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @SwaggerApiResponse(responseCode = "200", description = "Image upload success")
    @Operation(
        summary = "Upload member image",
        description = "Uploads a reference image to S3 and saves the metadata with the authenticated member."
    )
    fun uploadMemberImage(
        @AuthenticationPrincipal memberDetails: FocusMemberDetails,
        @RequestPart("image") image: MultipartFile
    ): ResponseEntity<ApiResponse.Success<MemberImageResponse>> {
        val response = memberImageService.uploadMemberImage(memberDetails.getMemberId(), image)
        return ResponseUtil.success("Image uploaded successfully.", response)
    }

    @FocusDeleteMapping("/{imageId}", authenticated = true)
    @SwaggerApiResponse(responseCode = "200", description = "Image delete success")
    @Operation(
        summary = "Delete member image",
        description = "Deletes the uploaded reference image for the authenticated member."
    )
    fun deleteMemberImage(
        @AuthenticationPrincipal memberDetails: FocusMemberDetails,
        @PathVariable imageId: String
    ): ResponseEntity<ApiResponse.Success<Unit>> {
        memberImageService.deleteMemberImage(memberDetails.getMemberId(), imageId)
        return ResponseUtil.success("Image deleted successfully.")
    }
}
