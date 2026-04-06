package com.capstone.focus.common.external.s3

import com.capstone.focus.common.config.S3Properties
import com.capstone.focus.common.exception.ApiException
import com.capstone.focus.common.exception.ErrorTitle
import com.github.f4b6a3.ulid.UlidCreator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest

interface ImageStorageService {
    fun uploadMemberImage(memberId: String, file: MultipartFile): UploadedImageInfo
    fun deleteMemberImage(objectKey: String)
}

data class UploadedImageInfo(
    val imageUrl: String,
    val objectKey: String,
    val originalFilename: String?,
    val contentType: String?,
    val sizeBytes: Long
)

@Service
class S3ImageStorageService(
    private val s3Client: S3Client,
    private val s3Properties: S3Properties
) : ImageStorageService {

    private val logger = LoggerFactory.getLogger(S3ImageStorageService::class.java)

    override fun uploadMemberImage(memberId: String, file: MultipartFile): UploadedImageInfo {
        validate(file)
        validateConfiguration()

        val objectKey = buildObjectKey(memberId, file.originalFilename)

        try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(s3Properties.bucket)
                    .key(objectKey)
                    .contentType(file.contentType)
                    .build(),
                RequestBody.fromBytes(file.bytes)
            )
        } catch (exception: Exception) {
            logger.error(
                "Failed to upload member image to S3. bucket={}, region={}, memberId={}, objectKey={}, contentType={}, message={}",
                s3Properties.bucket,
                s3Properties.region,
                memberId,
                objectKey,
                file.contentType,
                exception.message,
                exception
            )
            throw ApiException(ErrorTitle.FileUploadFail)
        }

        return UploadedImageInfo(
            imageUrl = buildImageUrl(objectKey),
            objectKey = objectKey,
            originalFilename = file.originalFilename,
            contentType = file.contentType,
            sizeBytes = file.size
        )
    }

    override fun deleteMemberImage(objectKey: String) {
        validateConfiguration()

        try {
            s3Client.deleteObject(
                DeleteObjectRequest.builder()
                    .bucket(s3Properties.bucket)
                    .key(objectKey)
                    .build()
            )
        } catch (exception: Exception) {
            logger.error(
                "Failed to delete member image from S3. bucket={}, region={}, objectKey={}, message={}",
                s3Properties.bucket,
                s3Properties.region,
                objectKey,
                exception.message,
                exception
            )
            throw ApiException(ErrorTitle.FileUploadFail)
        }
    }

    private fun validate(file: MultipartFile) {
        if (file.isEmpty) {
            throw ApiException(ErrorTitle.InvalidImageFile)
        }

        if (file.contentType.isNullOrBlank() || !file.contentType!!.startsWith("image/")) {
            throw ApiException(ErrorTitle.InvalidImageFile)
        }
    }

    private fun validateConfiguration() {
        if (s3Properties.bucket.isBlank()) {
            throw ApiException(ErrorTitle.FileUploadFail)
        }
    }

    private fun buildObjectKey(memberId: String, originalFilename: String?): String {
        val extension = originalFilename
            ?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() }
            ?.lowercase()

        val fileName = buildString {
            append(UlidCreator.getMonotonicUlid())
            if (extension != null) {
                append(".")
                append(extension)
            }
        }

        return "member-images/$memberId/$fileName"
    }

    private fun buildImageUrl(objectKey: String): String {
        if (s3Properties.publicBaseUrl.isNotBlank()) {
            return "${s3Properties.publicBaseUrl.trimEnd('/')}/$objectKey"
        }

        return "https://${s3Properties.bucket}.s3.${s3Properties.region}.amazonaws.com/$objectKey"
    }
}
