//
//  jsonMetadataRepository.swift
//  focus
//
//  Created by Codex on 4/7/26.
//

import Foundation

protocol MetadataFrameWriting: AnyObject {
    func startSession(sessionID: String)
    @discardableResult
    func appendFrame(sessionID: String, ptsUs: Int64, tracks: [TrackedFace]) -> Int
    func finishSession() throws -> URL?
    func reset()
}

final class JSONMetadataRepository: MetadataFrameWriting {
    private struct MetadataRoot: Encodable {
        let frames: [MetadataFrame]
    }

    private struct MetadataFrame: Encodable {
        let session_id: String
        let pts_us: Int64
        let faces: [MetadataFace]
    }

    private struct MetadataFace: Encodable {
        let tracking_id: Int
        let bbox: MetadataBBox
        let tdmm_raw: MetadataTDMMRaw
    }

    private struct MetadataBBox: Encodable {
        let x: Int
        let y: Int
        let width: Int
        let height: Int
    }

    private struct MetadataTDMMRaw: Encodable {
        let coeffs: [Float]
    }

    private let lock = NSLock()
    private let fileCoordinator: SessionFileCoordinator
    private let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return encoder
    }()

    private var activeSessionID: String?
    private var frames: [MetadataFrame] = []

    init(fileCoordinator: SessionFileCoordinator = SessionFileCoordinator()) {
        self.fileCoordinator = fileCoordinator
    }

    func startSession(sessionID: String) {
        lock.lock()
        activeSessionID = sessionID
        frames.removeAll(keepingCapacity: true)
        lock.unlock()
    }

    @discardableResult
    func appendFrame(sessionID: String, ptsUs: Int64, tracks: [TrackedFace]) -> Int {
        let metadataFaces = tracks.compactMap(makeFace)
        let frame = MetadataFrame(
            session_id: sessionID,
            pts_us: ptsUs,
            faces: metadataFaces
        )

        lock.lock()
        frames.append(frame)
        lock.unlock()

        return metadataFaces.count
    }

    func finishSession() throws -> URL? {
        let root: MetadataRoot

        lock.lock()
        defer {
            frames.removeAll()
            activeSessionID = nil
            lock.unlock()
        }

        guard activeSessionID != nil else {
            return nil
        }

        root = MetadataRoot(frames: frames)

        let data = try encoder.encode(root)
        let outputURL = try fileCoordinator.makeMetadataOutputURL()
        try data.write(to: outputURL, options: .atomic)
        return outputURL
    }

    func reset() {
        lock.lock()
        frames.removeAll()
        activeSessionID = nil
        lock.unlock()
    }

    private func makeFace(from track: TrackedFace) -> MetadataFace? {
        let policy = MetadataPolicy.evaluate(track: track)
        guard policy.shouldInclude, let tdmm = track.tdmm else {
            return nil
        }

        let bbox = MetadataPolicy.clampBBoxToInt(track.bbox)
        return MetadataFace(
            tracking_id: track.trackID,
            bbox: MetadataBBox(
                x: Int(bbox.x),
                y: Int(bbox.y),
                width: Int(bbox.width),
                height: Int(bbox.height)
            ),
            tdmm_raw: MetadataTDMMRaw(coeffs: tdmm.merged)
        )
    }
}
