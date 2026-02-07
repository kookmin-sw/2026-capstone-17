package com.kmu_focus.focusandroid.feature.detection.di

import com.kmu_focus.focusandroid.feature.detection.data.detector.YuNetOpenCVDetector
import com.kmu_focus.focusandroid.feature.detection.domain.config.DetectionConfig
import com.kmu_focus.focusandroid.feature.detection.domain.detector.FaceDetector
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DetectionModule {

    @Binds
    abstract fun bindFaceDetector(impl: YuNetOpenCVDetector): FaceDetector

    companion object {
        @Provides
        @Singleton
        fun provideDetectionConfig(): DetectionConfig = DetectionConfig()
    }
}
