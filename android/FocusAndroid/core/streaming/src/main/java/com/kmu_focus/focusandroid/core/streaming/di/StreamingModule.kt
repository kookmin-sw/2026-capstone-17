package com.kmu_focus.focusandroid.core.streaming.di

import com.kmu_focus.focusandroid.core.streaming.data.repository.SrtStreamRepositoryImpl
import com.kmu_focus.focusandroid.core.streaming.domain.repository.SrtStreamRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StreamingModule {

    @Binds
    @Singleton
    abstract fun bindSrtStreamRepository(
        impl: SrtStreamRepositoryImpl,
    ): SrtStreamRepository
}
