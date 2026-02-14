package com.kmu_focus.focusandroid.core.ai.di

import com.kmu_focus.focusandroid.core.ai.data.model3dmm.TFLiteFacial3DMMDetector
import com.kmu_focus.focusandroid.core.ai.data.yunet.YuNetOpenCVDetector
import com.kmu_focus.focusandroid.core.ai.domain.config.DetectionConfig
import com.kmu_focus.focusandroid.core.ai.domain.detector.FaceDetector
import com.kmu_focus.focusandroid.core.ai.domain.detector.Facial3DMMExtractor
import com.kmu_focus.focusandroid.core.ai.data.recognition.OwnerEmbeddingStore
import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.OwnerEmbeddingProvider
import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.OwnerOtherClassifier
import com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.TrackLabelState
import com.kmu_focus.focusandroid.core.ai.domain.detector.tracking.FaceTracker
import com.kmu_focus.focusandroid.core.ai.domain.detector.tracking.TrackingMethod
import com.kmu_focus.focusandroid.core.ai.domain.detector.tracking.createFaceTracker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindFaceDetector(impl: YuNetOpenCVDetector): FaceDetector

    @Binds
    @Singleton
    abstract fun bindFacial3DMMExtractor(impl: TFLiteFacial3DMMDetector): Facial3DMMExtractor

    @Binds
    @Singleton
    abstract fun bindOwnerEmbeddingProvider(impl: OwnerEmbeddingStore): OwnerEmbeddingProvider

    @Binds
    @Singleton
    abstract fun bindOwnerAdder(impl: OwnerEmbeddingStore): com.kmu_focus.focusandroid.core.ai.domain.detector.recognition.OwnerAdder

    companion object {
        @Provides
        @Singleton
        fun provideDetectionConfig(): DetectionConfig = DetectionConfig()

        @Provides
        @Singleton
        fun provideFaceTracker(): FaceTracker = createFaceTracker(TrackingMethod.IoU_3DMM)

        @Provides
        @Singleton
        fun provideOwnerOtherClassifier(store: OwnerEmbeddingProvider): OwnerOtherClassifier =
            OwnerOtherClassifier(store)

        @Provides
        @Singleton
        fun provideTrackLabelState(classifier: OwnerOtherClassifier): TrackLabelState =
            TrackLabelState(classifier)
    }
}
