package com.kmu_focus.focusandroid.core.metadata.di

import android.content.Context
import android.os.Environment
import com.kmu_focus.focusandroid.core.metadata.data.local.JsonMetadataRepository
import com.kmu_focus.focusandroid.core.metadata.domain.repository.MetadataRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MetadataOutputDir

@Module
@InstallIn(SingletonComponent::class)
abstract class MetadataModule {

    @Binds
    abstract fun bindMetadataRepository(
        impl: JsonMetadataRepository,
    ): MetadataRepository

    companion object {
        @Provides
        @Singleton
        @MetadataOutputDir
        @JvmStatic
        fun provideMetadataOutputDir(
            @ApplicationContext context: Context,
        ): File {
            val externalDocuments = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            return File(externalDocuments ?: context.filesDir, "metadata").apply { mkdirs() }
        }
    }
}
