package com.kmu_focus.focusandroid.feature.detection.data.detector

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

// assets의 모델 파일을 filesDir로 복사하여 네이티브 라이브러리가 접근 가능하게 함
// OpenCV FaceDetectorYN.create()는 파일 경로(String)를 요구하므로 assets 직접 접근 불가
@Singleton
class ModelFileProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getModelPath(modelName: String): String {
        val modelFile = File(context.filesDir, modelName)
        if (!modelFile.exists()) {
            context.assets.open(modelName).use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return modelFile.absolutePath
    }
}
