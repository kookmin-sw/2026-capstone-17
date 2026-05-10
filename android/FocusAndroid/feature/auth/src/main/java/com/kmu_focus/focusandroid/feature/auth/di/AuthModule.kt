package com.kmu_focus.focusandroid.feature.auth.di

import com.kmu_focus.focusandroid.feature.auth.data.remote.AuthApi
import com.kmu_focus.focusandroid.feature.auth.data.repository.KakaoAuthRepositoryImpl
import com.kmu_focus.focusandroid.feature.auth.data.repository.ServerAuthRepositoryImpl
import com.kmu_focus.focusandroid.feature.auth.data.session.SessionExpiredHandlerImpl
import com.kmu_focus.focusandroid.core.network.domain.SessionExpiredHandler
import com.kmu_focus.focusandroid.feature.auth.domain.repository.KakaoAuthRepository
import com.kmu_focus.focusandroid.feature.auth.domain.repository.ServerAuthRepository
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
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindKakaoAuthRepository(
        impl: KakaoAuthRepositoryImpl,
    ): KakaoAuthRepository

    @Binds
    @Singleton
    abstract fun bindServerAuthRepository(
        impl: ServerAuthRepositoryImpl,
    ): ServerAuthRepository

    @Binds
    @Singleton
    abstract fun bindSessionExpiredHandler(
        impl: SessionExpiredHandlerImpl,
    ): SessionExpiredHandler

    companion object {
        @Provides
        @Singleton
        fun provideAuthApi(
            @Named("AppRetrofit") retrofit: Retrofit,
        ): AuthApi {
            return retrofit.create(AuthApi::class.java)
        }
    }
}
