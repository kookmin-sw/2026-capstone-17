//
//  constants.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation
import CoreGraphics

enum FocusConstants {
    static let enableRemoteSessionLifecycle = false
    static let enableRemoteMetadataStream = false
    static let serverBaseURLString = "http://127.0.0.1:8080/"
    static let metadataGRPCHost = "127.0.0.1"
    static let metadataGRPCPort = 50051
    static let metadataGRPCUseTLS = false

    static let yunetShortSide: CGFloat = 360
    static let yunetScoreThreshold: Float = 0.5
    static let yunetNmsThreshold: Float = 0.3
    static let yunetConfidenceThreshold: Float = 0.5
    static let yunetTopK: Int = 5000

    static let tdmmInputSize = CGSize(width: 128, height: 128)
    static let arcFaceInputSize = CGSize(width: 112, height: 112)

    static let trackIouWeight: Float = 0.6
    static let trackCosineWeight: Float = 0.4
    static let maxIouDistance: Float = 0.7
    static let maxCosineDistance: Float = 0.4
    static let maxCosineRelaxedMultiplier: Float = 1.2

    static let nInit: Int = 1
    static let maxAge: Int = 30

    static let skipFrames: Int = 5
    static let collectFrames: Int = 3
    static let frontalThreshold: CGFloat = 0.4
    static let ownerSimilarityThreshold: Float = 0.5

    static let minEmbeddingCropSize: CGFloat = 16
    static let minManualOwnerFaceSize: CGFloat = 16
    static let arcFaceMinAlignmentAngleDeg: CGFloat = 1.5
    static let ownerSnapshotRotationDegrees: CGFloat = 0

    static let ownerRegistrationRetryIntervalMs: Int = 250
    static let ownerUpgradeRetryIntervalMs: Int = 400
    static let ownerUpgradeSimilarityThreshold: Float = 0.65
    static let soleOwnerLockConfirmFrames: Int = 3
    static let soleOwnerLockGraceFrames: Int = 8
    static let soleOwnerLockTransferMaxCost: Float = 0.45
    static let previewAnalysisStride: Int = 1
    static let previewOverlayMaxMissedFrames: Int = 3
    static let recordingMaskMaxMissedFrames: Int = 0
    static let previewBoxInterpolationFactor: CGFloat = 0.36
    static let previewOwnerLatchMemoryFrames: Int = 60

    static let maxSimultaneousMaskFaces = 8
    static let mosaicBlockSize: Int = 70
    static let privacyMaskHorizontalBiasRatio: CGFloat = -0.08

    static let ptsScaleMicroseconds: Double = 1_000_000.0
    static let avDriftToleranceMs: Double = 40.0
}
