package com.kmu_focus.focusandroid.feature.video.di

import android.content.Context
import com.kmu_focus.focusandroid.feature.video.data.decoder.VideoFrameDecoder
import com.kmu_focus.focusandroid.feature.video.data.decoder.VideoFrameDecoderImpl
import com.kmu_focus.focusandroid.feature.video.data.local.VideoLocalDataSource
import com.kmu_focus.focusandroid.feature.video.data.local.VideoLocalDataSourceImpl
import com.kmu_focus.focusandroid.feature.video.data.processor.FrameProcessor
import com.kmu_focus.focusandroid.feature.video.data.repository.ImageRepositoryImpl
import com.kmu_focus.focusandroid.feature.video.data.repository.VideoRepositoryImpl
import com.kmu_focus.focusandroid.feature.video.data.transcoder.VideoTranscoder
import com.kmu_focus.focusandroid.feature.video.domain.repository.ImageRepository
import com.kmu_focus.focusandroid.feature.video.domain.repository.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VideoModule {

    @Binds
    @Singleton
    abstract fun bindVideoRepository(impl: VideoRepositoryImpl): VideoRepository

    @Binds
    @Singleton
    abstract fun bindImageRepository(impl: ImageRepositoryImpl): ImageRepository

    @Binds
    @Singleton
    abstract fun bindVideoLocalDataSource(impl: VideoLocalDataSourceImpl): VideoLocalDataSource

    @Binds
    @Singleton
    abstract fun bindVideoFrameDecoder(impl: VideoFrameDecoderImpl): VideoFrameDecoder

    companion object {
        @Provides
        @Singleton
        @IoDispatcher
        @JvmStatic
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @Singleton
        @JvmStatic
        fun provideVideoTranscoder(
            @ApplicationContext context: Context,
            frameProcessor: FrameProcessor
        ): VideoTranscoder = VideoTranscoder(context, frameProcessor)
    }
}
