package com.kmu_focus.focusandroid.feature.broadcast.di

import com.kmu_focus.focusandroid.feature.broadcast.data.remote.BroadcastApi
import com.kmu_focus.focusandroid.feature.broadcast.data.repository.BroadcastRepositoryImpl
import com.kmu_focus.focusandroid.feature.broadcast.domain.repository.BroadcastRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class BroadcastModule {

    @Binds
    @Singleton
    abstract fun bindBroadcastRepository(
        impl: BroadcastRepositoryImpl,
    ): BroadcastRepository

    companion object {
        @Provides
        @Singleton
        fun provideBroadcastApi(
            @Named("AppRetrofit") retrofit: Retrofit,
        ): BroadcastApi {
            return retrofit.create(BroadcastApi::class.java)
        }
    }
}
