//
//  multiplexMetadataRepository.swift
//  focus
//
//  Created by Codex on 5/11/26.
//

import Foundation

protocol MetadataSessionSideEffecting: Sendable {
    func startSession(sessionID: String) async
    func appendFrame(sessionID: String, ptsUs: Int64, tracks: [TrackedFace]) async
    func finishSession() async
    func reset() async
}

final class MultiplexMetadataRepository: MetadataFrameWriting {
    private let localRepository: MetadataFrameWriting
    private let sideEffect: (any MetadataSessionSideEffecting)?

    init(
        localRepository: MetadataFrameWriting,
        sideEffect: (any MetadataSessionSideEffecting)? = nil
    ) {
        self.localRepository = localRepository
        self.sideEffect = sideEffect
    }

    func startSession(sessionID: String) {
        localRepository.startSession(sessionID: sessionID)

        guard let sideEffect else { return }
        Task {
            await sideEffect.startSession(sessionID: sessionID)
        }
    }

    @discardableResult
    func appendFrame(sessionID: String, ptsUs: Int64, tracks: [TrackedFace]) -> Int {
        let metadataFaceCount = localRepository.appendFrame(
            sessionID: sessionID,
            ptsUs: ptsUs,
            tracks: tracks
        )

        guard let sideEffect else {
            return metadataFaceCount
        }

        Task {
            await sideEffect.appendFrame(
                sessionID: sessionID,
                ptsUs: ptsUs,
                tracks: tracks
            )
        }

        return metadataFaceCount
    }

    func finishSession() throws -> URL? {
        let outputURL = try localRepository.finishSession()

        if let sideEffect {
            Task {
                await sideEffect.finishSession()
            }
        }

        return outputURL
    }

    func reset() {
        localRepository.reset()

        if let sideEffect {
            Task {
                await sideEffect.reset()
            }
        }
    }
}
