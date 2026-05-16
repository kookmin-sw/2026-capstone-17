//
//  srtBroadcastStreamer.swift
//  focus
//
//  Created by Codex on 5/16/26.
//

import AVFoundation
import Foundation

#if canImport(SRTHaishinKit)
import SRTHaishinKit
#endif

final class SRTBroadcastStreamer {
    enum State: Equatable {
        case idle
        case connecting
        case connected
        case failed(String)
        case closed
    }

    private let backend: any SRTBroadcastBackend

    init() {
        backend = DefaultSRTBroadcastBackend()
    }

    func start(host: String, port: Int, streamKey: String) async throws {
        try await backend.start(host: host, port: port, streamKey: streamKey)
    }

    func appendVideo(_ sampleBuffer: CMSampleBuffer) {
        Task {
            await backend.appendVideo(sampleBuffer)
        }
    }

    func appendAudio(_ sampleBuffer: CMSampleBuffer) {
        Task {
            await backend.appendAudio(sampleBuffer)
        }
    }

    func stop() async {
        await backend.stop()
    }
}

private protocol SRTBroadcastBackend: Sendable {
    func start(host: String, port: Int, streamKey: String) async throws
    func appendVideo(_ sampleBuffer: CMSampleBuffer) async
    func appendAudio(_ sampleBuffer: CMSampleBuffer) async
    func stop() async
}

#if canImport(SRTHaishinKit)
private actor DefaultSRTBroadcastBackend: SRTBroadcastBackend {
    private let connection = SRTConnection()
    private let stream: SRTStream
    private var isPublishing = false

    init() {
        self.stream = SRTStream(connection: connection)
    }

    func start(host: String, port: Int, streamKey: String) async throws {
        guard !isPublishing else { return }

        let urlString = "srt://\(host):\(port)?streamid=publish:live/\(streamKey)"
        guard let url = URL(string: urlString) else {
            throw NSError(
                domain: "SRTBroadcastStreamer",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "유효하지 않은 SRT URL입니다."]
            )
        }

        try await connection.open(url, mode: .caller)
        try await stream.publish()
        isPublishing = true
    }

    func appendVideo(_ sampleBuffer: CMSampleBuffer) async {
        guard isPublishing else { return }
        await stream.append(sampleBuffer)
    }

    func appendAudio(_ sampleBuffer: CMSampleBuffer) async {
        guard isPublishing else { return }
        await stream.append(sampleBuffer)
    }

    func stop() async {
        guard isPublishing else { return }
        isPublishing = false
        try? await connection.close()
    }
}
#else
private actor DefaultSRTBroadcastBackend: SRTBroadcastBackend {
    func start(host: String, port: Int, streamKey: String) async throws {
        throw NSError(
            domain: "SRTBroadcastStreamer",
            code: -99,
            userInfo: [NSLocalizedDescriptionKey: "SRTHaishinKit 패키지가 연결되지 않았습니다."]
        )
    }

    func appendVideo(_ sampleBuffer: CMSampleBuffer) async {}
    func appendAudio(_ sampleBuffer: CMSampleBuffer) async {}
    func stop() async {}
}
#endif
