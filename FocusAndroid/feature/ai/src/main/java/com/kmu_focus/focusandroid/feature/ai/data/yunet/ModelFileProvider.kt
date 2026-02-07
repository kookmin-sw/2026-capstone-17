package com.kmu_focus.focusandroid.feature.ai.data.yunet

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

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
