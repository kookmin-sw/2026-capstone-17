package com.kmu_focus.focusandroid.core.media.di

import com.kmu_focus.focusandroid.core.media.data.local.VideoLocalDataSource
import com.kmu_focus.focusandroid.core.media.data.local.VideoLocalDataSourceImpl
import com.kmu_focus.focusandroid.core.media.data.recorder.RealTimeRecorder
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreMediaModule {

    @Binds
    @Singleton
    abstract fun bindVideoLocalDataSource(
        impl: VideoLocalDataSourceImpl,
    ): VideoLocalDataSource

    companion object {
        @Provides
        @Singleton
        @IoDispatcher
        @JvmStatic
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @Singleton
        @JvmStatic
        fun provideRealTimeRecorder(): RealTimeRecorder = RealTimeRecorder(
            enableBackgroundDrain = System.getProperty("focus.test.mode") != "true",
        )
    }
}
