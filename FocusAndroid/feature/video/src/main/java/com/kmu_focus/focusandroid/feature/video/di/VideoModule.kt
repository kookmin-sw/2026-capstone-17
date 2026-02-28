package com.kmu_focus.focusandroid.feature.video.di

import android.content.Context
import com.kmu_focus.focusandroid.feature.video.data.decoder.VideoFrameDecoder
import com.kmu_focus.focusandroid.feature.video.data.decoder.VideoFrameDecoderImpl
import com.kmu_focus.focusandroid.core.media.data.processor.FrameProcessor
import com.kmu_focus.focusandroid.feature.video.data.repository.ImageRepositoryImpl
import com.kmu_focus.focusandroid.feature.video.data.repository.PlaybackAnalysisRepositoryImpl
import com.kmu_focus.focusandroid.feature.video.data.repository.RecordingRepositoryImpl
import com.kmu_focus.focusandroid.feature.video.data.repository.VideoRepositoryImpl
import com.kmu_focus.focusandroid.core.media.data.recorder.AudioTrackExtractor
import com.kmu_focus.focusandroid.feature.video.data.transcoder.VideoTranscoder
import com.kmu_focus.focusandroid.feature.video.domain.repository.ImageRepository
import com.kmu_focus.focusandroid.feature.video.domain.repository.PlaybackAnalysisRepository
import com.kmu_focus.focusandroid.feature.video.domain.repository.RecordingRepository
import com.kmu_focus.focusandroid.feature.video.domain.repository.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
    abstract fun bindRecordingRepository(impl: RecordingRepositoryImpl): RecordingRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackAnalysisRepository(impl: PlaybackAnalysisRepositoryImpl): PlaybackAnalysisRepository

    @Binds
    @Singleton
    abstract fun bindVideoFrameDecoder(impl: VideoFrameDecoderImpl): VideoFrameDecoder

    companion object {
        @Provides
        @Singleton
        @JvmStatic
        fun provideVideoTranscoder(
            @ApplicationContext context: Context,
            frameProcessor: FrameProcessor
        ): VideoTranscoder = VideoTranscoder(context, frameProcessor)

        @Provides
        @Singleton
        @JvmStatic
        fun provideAudioTrackExtractorFactory(
            @ApplicationContext context: Context,
        ): AudioTrackExtractor.Factory = AudioTrackExtractor.Factory { sourceUri ->
            AudioTrackExtractor.create(sourceUri, context)
        }
    }
}
