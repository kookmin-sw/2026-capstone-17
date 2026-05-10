package com.kmu_focus.focusandroid.feature.account.di

import com.kmu_focus.focusandroid.feature.account.BuildConfig
import com.kmu_focus.focusandroid.feature.account.data.oauth.ChzzkOAuthConfig
import com.kmu_focus.focusandroid.feature.account.data.remote.AccountApi
import com.kmu_focus.focusandroid.feature.account.data.repository.AccountRepositoryImpl
import com.kmu_focus.focusandroid.feature.account.domain.repository.AccountRepository
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
abstract class AccountModule {

    @Binds
    @Singleton
    abstract fun bindAccountRepository(
        impl: AccountRepositoryImpl,
    ): AccountRepository

    companion object {
        @Provides
        @Singleton
        fun provideAccountApi(
            @Named("AppRetrofit") retrofit: Retrofit,
        ): AccountApi {
            return retrofit.create(AccountApi::class.java)
        }

        @Provides
        @Singleton
        fun provideChzzkOAuthConfig(): ChzzkOAuthConfig {
            return ChzzkOAuthConfig(
                clientId = BuildConfig.CHZZK_CLIENT_ID,
                redirectUri = BuildConfig.CHZZK_REDIRECT_URI,
                authBaseUrl = BuildConfig.CHZZK_AUTH_BASE_URL,
            )
        }
    }
}
