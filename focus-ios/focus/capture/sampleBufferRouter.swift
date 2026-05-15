//
//  sampleBufferRouter.swift
//  focus
//
//  Created by 이동언 on 3/7/26.
//

import Foundation
import AVFoundation

final class SampleBufferRouter {
    typealias VideoHandler = (CMSampleBuffer) -> Void
    typealias AudioHandler = (CMSampleBuffer) -> Void
    typealias AnySampleHandler = (CaptureSample) -> Void

    var onVideoSample: VideoHandler?
    var onAudioSample: AudioHandler?
    var onAnySample: AnySampleHandler?

    func route(sampleBuffer: CMSampleBuffer, mediaType: CaptureMediaType) {
        let sample = CaptureSample(mediaType: mediaType, sampleBuffer: sampleBuffer)
        onAnySample?(sample)

        switch mediaType {
        case .video:
            onVideoSample?(sampleBuffer)
        case .audio:
            onAudioSample?(sampleBuffer)
        }
    }

    func clearHandlers() {
        onVideoSample = nil
        onAudioSample = nil
        onAnySample = nil
    }
}
