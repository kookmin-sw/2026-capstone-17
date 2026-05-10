package com.kmu_focus.focusandroid.core.network.di

import com.kmu_focus.focusandroid.core.network.BuildConfig
import com.kmu_focus.focusandroid.core.network.data.AuthInterceptor
import com.kmu_focus.focusandroid.core.network.data.SharedPrefsTokenStore
import com.kmu_focus.focusandroid.core.network.data.TokenAuthenticator
import com.kmu_focus.focusandroid.core.network.data.TokenRefreshService
import com.kmu_focus.focusandroid.core.network.domain.TokenStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun bindTokenStore(
        impl: SharedPrefsTokenStore,
    ): TokenStore

    companion object {
        @Provides
        @Singleton
        fun provideTokenRefreshService(
            tokenStore: TokenStore,
            @Named("RefreshOkHttpClient") refreshOkHttpClient: OkHttpClient,
        ): TokenRefreshService {
            return TokenRefreshService(
                tokenStore = tokenStore,
                baseUrl = BuildConfig.SERVER_BASE_URL,
                okHttpClient = refreshOkHttpClient,
            )
        }

        @Provides
        @Singleton
        @Named("RefreshOkHttpClient")
        fun provideRefreshOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .addInterceptor(createLoggingInterceptor())
                .build()
        }

        @Provides
        @Singleton
        fun provideOkHttpClient(
            authInterceptor: AuthInterceptor,
            tokenAuthenticator: TokenAuthenticator,
        ): OkHttpClient {
            return OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .authenticator(tokenAuthenticator)
                .addInterceptor(createLoggingInterceptor())
                .build()
        }

        private fun createLoggingInterceptor(): HttpLoggingInterceptor {
            return HttpLoggingInterceptor().apply {
                level = if (com.kmu_focus.focusandroid.core.network.BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
        }

        @Provides
        @Singleton
        @Named("AppRetrofit")
        fun provideRetrofit(
            okHttpClient: OkHttpClient,
        ): Retrofit {
            return Retrofit.Builder()
                .baseUrl(BuildConfig.SERVER_BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
    }
}
