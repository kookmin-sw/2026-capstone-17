package com.kmu_focus.focusandroid.core.grpc.di

import com.kmu_focus.focusandroid.core.grpc.BuildConfig
import com.kmu_focus.focusandroid.core.grpc.data.remote.FaceMetadataStreamManager
import com.kmu_focus.focusandroid.core.grpc.proto.FaceMetadataIngestServiceGrpc
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GrpcModule {
    @Provides
    @Singleton
    fun provideManagedChannel(): ManagedChannel {
        return OkHttpChannelBuilder
            .forAddress(BuildConfig.GRPC_SERVER_HOST, BuildConfig.GRPC_SERVER_PORT)
            .usePlaintext()
            .build()
    }

    @Provides
    @Singleton
    fun provideFaceMetadataStreamManager(
        channel: ManagedChannel,
    ): FaceMetadataStreamManager {
        return FaceMetadataStreamManager(
            FaceMetadataIngestServiceGrpc.newStub(channel),
        )
    }
}
