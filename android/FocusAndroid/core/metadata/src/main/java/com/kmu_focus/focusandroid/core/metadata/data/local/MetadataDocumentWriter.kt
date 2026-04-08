package com.kmu_focus.focusandroid.core.metadata.data.local

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class MetadataDocumentWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun write(json: String): Boolean = runCatching {
        val resolver = context.contentResolver
        val fileName = "metadata_${System.currentTimeMillis()}.json"
        val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/FocusAndroid"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values,
        ) ?: return false

        resolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8).use { writer ->
            checkNotNull(writer) { "문서 디렉토리 출력 스트림 생성 실패" }
            writer.write(json)
        }

        val publishValues = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        resolver.update(uri, publishValues, null, null)
        true
    }.getOrDefault(false)
}
