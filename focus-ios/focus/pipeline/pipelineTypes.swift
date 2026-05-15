//
//  pipelineTypes.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation
import CoreGraphics

enum TrackLabel: Equatable, Sendable {
    case pending
    case owner
    case other
}

struct TrackedFace: Equatable, Sendable {
    let trackID: Int
    var bbox: CGRect
    var landmarks: FaceLandmarks5?
    var tdmm: TDMMCoefficients?
    var label: TrackLabel
    var ownerID: UUID?
    var age: Int
    var missedFrames: Int
    var frontalEmbeddingSamples: [[Float]]
    var hasRetriedOther: Bool
    var framesSeen: Int
    var lastSeenFrameIndex: Int
}

enum PipelineState: Equatable {
    case idle
    case running
    case stopping
    case stopped
}

enum PipelineError: LocalizedError {
    case alreadyRunning
    case notRunning
    case missingPixelBuffer
    case invalidState(String)

    var errorDescription: String? {
        switch self {
        case .alreadyRunning:
            return "이미 파이프라인이 실행 중입니다."
        case .notRunning:
            return "파이프라인이 실행 중이 아닙니다."
        case .missingPixelBuffer:
            return "sampleBuffer에서 pixelBuffer를 가져오지 못했습니다."
        case .invalidState(let message):
            return "파이프라인 상태 오류: \(message)"
        }
    }
}

struct PipelineDebugSnapshot: Sendable {
    let frameIndex: Int
    let detectedFaceCount: Int
    let trackedFaceCount: Int
    let metadataFaceCount: Int
    let ptsUs: Int64
}

struct PipelineSessionOutputs: Sendable {
    let recordingURL: URL?
    let metadataURL: URL?
    let avatarVideoURL: URL?
    let avatarSchemaURL: URL?
}
