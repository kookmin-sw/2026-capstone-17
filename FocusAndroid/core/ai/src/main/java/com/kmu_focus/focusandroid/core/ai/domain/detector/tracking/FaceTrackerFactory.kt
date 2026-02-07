package com.kmu_focus.focusandroid.core.ai.domain.detector.tracking

fun createFaceTracker(method: TrackingMethod): FaceTracker = IoU3DMMTracker()
