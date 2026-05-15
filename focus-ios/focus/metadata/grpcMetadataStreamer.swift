//
//  grpcMetadataStreamer.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation
import GRPC
import NIOCore
import NIOPosix

final class GRPCMetadataStreamer {
    enum StreamState: Equatable {
        case idle
        case connecting
        case connected
        case closing
        case closed
        case failed(String)
    }

    private let host: String
    private let port: Int
    private let useTLS: Bool

    private var group: EventLoopGroup?
    private var channel: GRPCChannel?
    private var client: Focus_FocusStreamAsyncClient?

    private var requestStream: GRPCAsyncRequestStreamWriter<Focus_FrameMetadata>?
    private var responseTask: Task<Focus_StreamAck, Error>?

    private let stateQueue = DispatchQueue(label: "focus.metadata.grpc.state")
    private(set) var state: StreamState = .idle

    init(host: String, port: Int, useTLS: Bool = false) {
        self.host = host
        self.port = port
        self.useTLS = useTLS
    }

    func connect() async throws {
        try await stateQueue.syncAsync {
            self.state = .connecting
        }

        let group = MultiThreadedEventLoopGroup(numberOfThreads: 1)
        self.group = group

        let channel: GRPCChannel
        if useTLS {
            channel = try await GRPCChannelPool.with(
                target: .host(host, port: port),
                transportSecurity: .tls(.makeClientDefault()),
                eventLoopGroup: group
            )
        } else {
            channel = try await GRPCChannelPool.with(
                target: .host(host, port: port),
                transportSecurity: .plaintext,
                eventLoopGroup: group
            )
        }

        self.channel = channel
        self.client = Focus_FocusStreamAsyncClient(channel: channel)

        guard let client else {
            throw NSError(domain: "GRPCMetadataStreamer", code: -1, userInfo: [
                NSLocalizedDescriptionKey: "gRPC client 생성 실패"
            ])
        }

        let call = client.sendFrames()

        self.requestStream = call.requestStream
        self.responseTask = Task {
            try await call.response
        }

        try await stateQueue.syncAsync {
            self.state = .connected
        }
    }

    func send(_ message: Focus_FrameMetadata) async throws {
        guard let requestStream else {
            throw NSError(domain: "GRPCMetadataStreamer", code: -1, userInfo: [
                NSLocalizedDescriptionKey: "gRPC requestStream이 연결되지 않았습니다."
            ])
        }

        guard state == .connected else {
            throw NSError(domain: "GRPCMetadataStreamer", code: -2, userInfo: [
                NSLocalizedDescriptionKey: "gRPC state가 connected가 아닙니다."
            ])
        }

        try await requestStream.send(message)
    }

    func finish() async throws -> Focus_StreamAck? {
        try await stateQueue.syncAsync {
            self.state = .closing
        }

        try await requestStream?.finish()

        let ack = try await responseTask?.value

        try await shutdown()

        return ack
    }

    func cancel() async {
        responseTask?.cancel()
        await forceClose()
    }

    private func shutdown() async throws {
        try await channel?.close().get()
        try await group?.shutdownGracefully()
        requestStream = nil
        responseTask = nil
        client = nil
        channel = nil
        group = nil

        try await stateQueue.syncAsync {
            self.state = .closed
        }
    }

    private func forceClose() async {
        do {
            try await channel?.close().get()
        } catch {}

        do {
            try await group?.shutdownGracefully()
        } catch {}

        requestStream = nil
        responseTask = nil
        client = nil
        channel = nil
        group = nil

        stateQueue.sync {
            self.state = .closed
        }
    }
}

// MARK: - DispatchQueue async helper
private extension DispatchQueue {
    func syncAsync<T>(_ work: @escaping () throws -> T) async throws -> T {
        try await withCheckedThrowingContinuation { continuation in
            self.async {
                do {
                    continuation.resume(returning: try work())
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }
}

private extension EventLoopGroup {
    func shutdownGracefully() async throws {
        try await withCheckedThrowingContinuation { continuation in
            self.shutdownGracefully { error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: ())
                }
            }
        }
    }
}
