//
//  localRecorder.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation
import AVFoundation
import CoreVideo

final class LocalRecorder {
    enum RecorderState: Equatable {
        case idle
        case preparing
        case recording
        case finishing
        case finished
        case failed(String)
    }

    private let writingQueue = DispatchQueue(label: "focus.streaming.localRecorder", qos: .userInitiated)
    private let lock = NSLock()

    private(set) var state: RecorderState = .idle

    private var writer: AVAssetWriter?
    private var videoInput: AVAssetWriterInput?
    private var audioInput: AVAssetWriterInput?
    private var pixelBufferAdaptor: AVAssetWriterInputPixelBufferAdaptor?
    private var recorderInput: PixelBufferRecorderInput?

    private var outputURL: URL?
    private var hasStartedSession = false
    private var sessionStartTime: CMTime?
    private var videoSize: CGSize = .zero

    func prepareRecording(
        outputURL: URL,
        videoSize: CGSize,
        fileType: AVFileType = .mp4,
        averageBitRate: Int? = nil,
        keyFrameIntervalSeconds: Int = 2
    ) throws {
        lock.lock()
        defer { lock.unlock() }

        guard state == .idle || state == .finished else { return }

        self.state = .preparing
        self.outputURL = outputURL
        self.videoSize = videoSize
        self.hasStartedSession = false
        self.sessionStartTime = nil

        if FileManager.default.fileExists(atPath: outputURL.path) {
            try FileManager.default.removeItem(at: outputURL)
        }

        let writer = try AVAssetWriter(outputURL: outputURL, fileType: fileType)
        let bitRate = averageBitRate ?? Self.defaultBitRate(for: videoSize)

        let videoSettings: [String: Any] = [
            AVVideoCodecKey: AVVideoCodecType.h264,
            AVVideoWidthKey: Int(videoSize.width),
            AVVideoHeightKey: Int(videoSize.height),
            AVVideoCompressionPropertiesKey: [
                AVVideoAverageBitRateKey: bitRate,
                AVVideoMaxKeyFrameIntervalDurationKey: keyFrameIntervalSeconds,
                AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel
            ]
        ]

        let videoInput = AVAssetWriterInput(mediaType: .video, outputSettings: videoSettings)
        videoInput.expectsMediaDataInRealTime = true

        let sourceAttrs: [String: Any] = [
            kCVPixelBufferPixelFormatTypeKey as String: Int(kCVPixelFormatType_32BGRA),
            kCVPixelBufferWidthKey as String: Int(videoSize.width),
            kCVPixelBufferHeightKey as String: Int(videoSize.height),
            kCVPixelBufferIOSurfacePropertiesKey as String: [:]
        ]

        let adaptor = AVAssetWriterInputPixelBufferAdaptor(
            assetWriterInput: videoInput,
            sourcePixelBufferAttributes: sourceAttrs
        )

        let audioSettings: [String: Any] = [
            AVFormatIDKey: kAudioFormatMPEG4AAC,
            AVNumberOfChannelsKey: 1,
            AVSampleRateKey: 44_100,
            AVEncoderBitRateKey: 128_000
        ]

        let audioInput = AVAssetWriterInput(mediaType: .audio, outputSettings: audioSettings)
        audioInput.expectsMediaDataInRealTime = true

        guard writer.canAdd(videoInput) else {
            throw NSError(domain: "LocalRecorder", code: -1, userInfo: [
                NSLocalizedDescriptionKey: "video input을 writer에 추가할 수 없습니다."
            ])
        }
        writer.add(videoInput)

        guard writer.canAdd(audioInput) else {
            throw NSError(domain: "LocalRecorder", code: -2, userInfo: [
                NSLocalizedDescriptionKey: "audio input을 writer에 추가할 수 없습니다."
            ])
        }
        writer.add(audioInput)

        self.writer = writer
        self.videoInput = videoInput
        self.audioInput = audioInput
        self.pixelBufferAdaptor = adaptor
        self.recorderInput = PixelBufferRecorderInput(adaptor: adaptor, writerInput: videoInput)
        self.state = .recording
    }

    func appendVideoPixelBuffer(_ pixelBuffer: CVPixelBuffer, pts: CMTime) {
        writingQueue.async { [weak self] in
            guard let self else { return }
            self.appendVideoPixelBufferSync(pixelBuffer, pts: pts)
        }
    }

    func appendAudioSampleBuffer(_ sampleBuffer: CMSampleBuffer) {
        writingQueue.async { [weak self] in
            guard let self else { return }
            self.appendAudioSampleBufferSync(sampleBuffer)
        }
    }

    private func appendVideoPixelBufferSync(_ pixelBuffer: CVPixelBuffer, pts: CMTime) {
        guard case .recording = state,
              let writer,
              let recorderInput else { return }

        startSessionIfNeeded(writer: writer, startTime: pts)

        let success = recorderInput.append(pixelBuffer: pixelBuffer, withPresentationTime: pts)
        if !success {
            fail("비디오 pixelBuffer append 실패")
        }
    }

    private func appendAudioSampleBufferSync(_ sampleBuffer: CMSampleBuffer) {
        guard case .recording = state,
              let writer,
              let audioInput else { return }

        let pts = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
        startSessionIfNeeded(writer: writer, startTime: pts)

        guard audioInput.isReadyForMoreMediaData else { return }

        if !audioInput.append(sampleBuffer) {
            fail("오디오 sampleBuffer append 실패")
        }
    }

    private func startSessionIfNeeded(writer: AVAssetWriter, startTime: CMTime) {
        guard !hasStartedSession else { return }

        if writer.status == .unknown {
            writer.startWriting()
            writer.startSession(atSourceTime: startTime)
            hasStartedSession = true
            sessionStartTime = startTime
        }
    }

    func finishWriting(_ completion: @escaping () -> Void) {
        writingQueue.async { [weak self] in
            guard let self else {
                completion()
                return
            }

            guard case .recording = self.state,
                  let writer else {
                completion()
                return
            }

            self.state = .finishing

            self.videoInput?.markAsFinished()
            self.audioInput?.markAsFinished()

            writer.finishWriting { [weak self] in
                guard let self else {
                    completion()
                    return
                }

                self.lock.lock()
                defer { self.lock.unlock() }

                if writer.status == .completed {
                    self.state = .finished
                } else {
                    self.state = .failed(writer.error?.localizedDescription ?? "writer finish 실패")
                }

                self.writer = nil
                self.videoInput = nil
                self.audioInput = nil
                self.pixelBufferAdaptor = nil
                self.recorderInput = nil
                self.hasStartedSession = false
                self.sessionStartTime = nil

                completion()
            }
        }
    }

    func cancelWriting() {
        writingQueue.async { [weak self] in
            guard let self else { return }
            self.writer?.cancelWriting()
            self.cleanupAfterCancel()
        }
    }

    private func cleanupAfterCancel() {
        lock.lock()
        defer { lock.unlock() }

        writer = nil
        videoInput = nil
        audioInput = nil
        pixelBufferAdaptor = nil
        recorderInput = nil
        hasStartedSession = false
        sessionStartTime = nil
        state = .idle
    }

    private func fail(_ message: String) {
        lock.lock()
        state = .failed(message)
        lock.unlock()
    }

    var currentOutputURL: URL? {
        lock.lock()
        defer { lock.unlock() }
        return outputURL
    }

    private static func defaultBitRate(for videoSize: CGSize) -> Int {
        let width = max(videoSize.width, videoSize.height)
        let height = min(videoSize.width, videoSize.height)
        let pixels = width * height

        if pixels <= (1280.0 * 720.0) {
            return 4_000_000
        } else if pixels >= (3840.0 * 2160.0) {
            return 15_000_000
        } else if pixels >= (1920.0 * 1080.0) {
            return 8_000_000
        } else {
            let lowerPixels: CGFloat = 1280.0 * 720.0
            let upperPixels: CGFloat = 1920.0 * 1080.0
            let progress = max(0, min(1, (pixels - lowerPixels) / (upperPixels - lowerPixels)))
            let bitRate: CGFloat = 4_000_000.0 + ((8_000_000.0 - 4_000_000.0) * progress)
            return Int(bitRate.rounded())
        }
    }
}
