package com.kmu_focus.focusandroid.feature.ai.domain.detector.tracking

fun createFaceTracker(method: TrackingMethod): FaceTracker = IoU3DMMTracker()
