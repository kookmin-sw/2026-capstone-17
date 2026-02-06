package com.kmu_focus.focusandroid.di

import com.kmu_focus.focusandroid.data.local.VideoLocalDataSource
import com.kmu_focus.focusandroid.data.local.VideoLocalDataSourceImpl
import com.kmu_focus.focusandroid.data.repository.VideoRepositoryImpl
import com.kmu_focus.focusandroid.domain.repository.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
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
    abstract fun bindVideoLocalDataSource(impl: VideoLocalDataSourceImpl): VideoLocalDataSource
}
