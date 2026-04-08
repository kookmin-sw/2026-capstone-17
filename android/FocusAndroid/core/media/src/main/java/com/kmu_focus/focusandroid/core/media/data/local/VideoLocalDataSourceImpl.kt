package com.kmu_focus.focusandroid.core.media.data.local

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

class VideoLocalDataSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : VideoLocalDataSource {

    override suspend fun copyVideoToInternalStorage(sourceUri: String): String =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(sourceUri)
            val videosDir = File(context.filesDir, "videos").apply {
                if (!exists()) mkdirs()
            }
            val fileName = "video_${UUID.randomUUID()}.mp4"
            val destFile = File(videosDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("URI를 열 수 없습니다: $sourceUri")

            destFile.absolutePath
        }

    override suspend fun saveVideoToGallery(sourceUri: String): String =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(sourceUri)
            val fileName = "video_${UUID.randomUUID()}.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Focus")
            }
            val insertUri = context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: throw IllegalStateException("갤러리 저장 실패: insert 실패")

            context.contentResolver.openInputStream(uri)?.use { input ->
                context.contentResolver.openOutputStream(insertUri)?.use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("URI를 열 수 없습니다: $sourceUri")

            insertUri.toString()
        }

    override fun createTempOutputFile(): File {
        val cacheDir = File(context.cacheDir, "transcode").apply {
            if (!exists()) mkdirs()
        }
        return File(cacheDir, "transcode_${UUID.randomUUID()}.mp4")
    }

    override fun deleteFile(file: File): Boolean = file.delete()

    override suspend fun moveToGallery(file: File): String =
        withContext(Dispatchers.IO) {
            val fileName = "focus_${UUID.randomUUID()}.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Focus")
            }
            val insertUri = context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: throw IllegalStateException("갤러리 저장 실패: insert 실패")

            file.inputStream().use { input ->
                context.contentResolver.openOutputStream(insertUri)?.use { output ->
                    input.copyTo(output)
                }
            }
            file.delete()
            insertUri.toString()
        }
}
