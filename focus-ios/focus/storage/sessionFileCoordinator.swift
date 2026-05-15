//
//  sessionFileCoordinator.swift
//  focus
//
//  Created by Codex on 4/7/26.
//

import Foundation

final class SessionFileCoordinator {
    private let fileManager: FileManager

    init(fileManager: FileManager = .default) {
        self.fileManager = fileManager
    }

    func makeRecordingOutputURL(sessionID: String) throws -> URL {
        let baseDirectory = try ensureDirectory(
            documentsDirectory()
                .appendingPathComponent("Focus", isDirectory: true)
                .appendingPathComponent("Recordings", isDirectory: true)
        )

        return baseDirectory.appendingPathComponent("focus_\(sessionID).mp4")
    }

    func makeMetadataOutputURL() throws -> URL {
        let timestamp = Self.timestampFormatter.string(from: Date())

        do {
            let preferredDirectory = try ensureDirectory(
                documentsDirectory().appendingPathComponent("FocusAndroid", isDirectory: true)
            )
            return preferredDirectory.appendingPathComponent("metadata_\(timestamp).json")
        } catch {
            let fallbackDirectory = try ensureDirectory(
                documentsDirectory().appendingPathComponent("metadata", isDirectory: true)
            )
            return fallbackDirectory.appendingPathComponent("metadata_\(timestamp).json")
        }
    }

    func makeAvatarSchemaOutputURL(sessionID: String) throws -> URL {
        let baseDirectory = try ensureDirectory(
            documentsDirectory()
                .appendingPathComponent("Focus", isDirectory: true)
                .appendingPathComponent("AvatarDebug", isDirectory: true)
        )

        return baseDirectory.appendingPathComponent("focus_\(sessionID)_avatar_schema.json")
    }

    func makeAvatarVideoOutputURL(sessionID: String) throws -> URL {
        let baseDirectory = try ensureDirectory(
            documentsDirectory()
                .appendingPathComponent("Focus", isDirectory: true)
                .appendingPathComponent("AvatarDebug", isDirectory: true)
        )

        return baseDirectory.appendingPathComponent("focus_\(sessionID)_avatar_delivery.mp4")
    }

    func ownerSnapshotsDirectory() throws -> URL {
        let cacheDirectory = preferredURL(for: .cachesDirectory) ?? documentsDirectory()
        return try ensureDirectory(cacheDirectory.appendingPathComponent("owner_snapshots", isDirectory: true))
    }

    func makeOwnerSnapshotURL(identifier: String = UUID().uuidString) throws -> URL {
        try ownerSnapshotsDirectory().appendingPathComponent("owner_\(identifier).jpg")
    }

    private func preferredURL(for directory: FileManager.SearchPathDirectory) -> URL? {
        fileManager.urls(for: directory, in: .userDomainMask).first
    }

    private func documentsDirectory() -> URL {
        preferredURL(for: .documentDirectory) ?? fileManager.temporaryDirectory
    }

    @discardableResult
    private func ensureDirectory(_ url: URL) throws -> URL {
        try fileManager.createDirectory(at: url, withIntermediateDirectories: true, attributes: nil)
        return url
    }

    private static let timestampFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd_HHmmss"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter
    }()
}
