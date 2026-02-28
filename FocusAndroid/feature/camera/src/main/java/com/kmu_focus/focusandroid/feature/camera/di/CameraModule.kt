package com.kmu_focus.focusandroid.feature.camera.di

import com.kmu_focus.focusandroid.feature.camera.data.repository.CameraAnalysisRepositoryImpl
import com.kmu_focus.focusandroid.feature.camera.data.repository.CameraRecordingRepositoryImpl
import com.kmu_focus.focusandroid.feature.camera.domain.repository.CameraAnalysisRepository
import com.kmu_focus.focusandroid.feature.camera.domain.repository.CameraRecordingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CameraModule {

    @Binds
    @Singleton
    abstract fun bindCameraAnalysisRepository(
        impl: CameraAnalysisRepositoryImpl,
    ): CameraAnalysisRepository

    @Binds
    @Singleton
    abstract fun bindCameraRecordingRepository(
        impl: CameraRecordingRepositoryImpl,
    ): CameraRecordingRepository
}
