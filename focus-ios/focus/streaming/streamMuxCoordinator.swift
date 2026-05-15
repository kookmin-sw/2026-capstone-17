//
//  streamMuxCoordinator.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation
import AVFoundation
import CoreVideo

final class StreamMuxCoordinator {
    enum OutputMode: Equatable {
        case localRecording
        case srtPreferred
        case rtmpFallback
    }

    private let localRecorder: LocalRecorder
    private(set) var mode: OutputMode

    init(
        mode: OutputMode = .localRecording,
        localRecorder: LocalRecorder = LocalRecorder()
    ) {
        self.mode = mode
        self.localRecorder = localRecorder
    }

    func prepareLocalRecording(
        outputURL: URL,
        videoSize: CGSize
    ) throws {
        try localRecorder.prepareRecording(
            outputURL: outputURL,
            videoSize: videoSize,
            fileType: .mp4
        )
    }

    func appendVideo(pixelBuffer: CVPixelBuffer, pts: CMTime) {
        switch mode {
        case .localRecording, .srtPreferred, .rtmpFallback:
            localRecorder.appendVideoPixelBuffer(pixelBuffer, pts: pts)
        }
    }

    func appendAudio(sampleBuffer: CMSampleBuffer) {
        switch mode {
        case .localRecording, .srtPreferred, .rtmpFallback:
            localRecorder.appendAudioSampleBuffer(sampleBuffer)
        }
    }

    func finish(completion: @escaping () -> Void) {
        switch mode {
        case .localRecording, .srtPreferred, .rtmpFallback:
            localRecorder.finishWriting(completion)
        }
    }

    func cancel() {
        localRecorder.cancelWriting()
    }

    var outputURL: URL? {
        localRecorder.currentOutputURL
    }

    var recorderState: LocalRecorder.RecorderState {
        localRecorder.state
    }
}
