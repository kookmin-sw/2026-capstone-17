package com.kmu_focus.focusandroid.data.local

import android.content.Context
import android.net.Uri
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
        // ContentResolver IO는 메인 스레드 블로킹 방지를 위해 IO 디스패처에서 실행
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
}
