//
//  ownerEmbeddingStore.swift
//  focus
//
//  Created by Codex on 4/7/26.
//

import Foundation

struct OwnerProfileSummary: Identifiable, Equatable, Sendable {
    let id: UUID
    let displayName: String
    let snapshotURL: URL?
    let embeddingCount: Int
}

final class OwnerEmbeddingStore {
    struct OwnerRecord: Equatable, Sendable {
        let id: UUID
        var displayName: String
        var snapshotURL: URL?
        var embeddings: [[Float]]
        let createdAt: Date
    }

    private let lock = NSLock()
    private var owners: [OwnerRecord] = []

    @discardableResult
    func addOwner(
        embedding: [Float],
        displayName: String? = nil,
        snapshotURL: URL? = nil
    ) -> OwnerRecord {
        let record = OwnerRecord(
            id: UUID(),
            displayName: displayName ?? defaultDisplayName(for: owners.count + 1),
            snapshotURL: snapshotURL,
            embeddings: [embedding],
            createdAt: Date()
        )

        lock.lock()
        owners.append(record)
        lock.unlock()

        return record
    }

    @discardableResult
    func replaceOwnerEmbedding(
        ownerID: UUID,
        embedding: [Float],
        snapshotURL: URL? = nil
    ) -> OwnerRecord? {
        lock.lock()
        defer { lock.unlock() }

        guard let index = owners.firstIndex(where: { $0.id == ownerID }) else {
            return nil
        }

        owners[index].embeddings = [embedding]
        if let snapshotURL {
            owners[index].snapshotURL = snapshotURL
        }
        return owners[index]
    }

    @discardableResult
    func removeOwner(ownerID: UUID) -> OwnerRecord? {
        lock.lock()
        defer { lock.unlock() }

        guard let index = owners.firstIndex(where: { $0.id == ownerID }) else {
            return nil
        }

        return owners.remove(at: index)
    }

    func allOwners() -> [OwnerRecord] {
        lock.lock()
        defer { lock.unlock() }
        return owners
    }

    func summaries() -> [OwnerProfileSummary] {
        allOwners().map {
            OwnerProfileSummary(
                id: $0.id,
                displayName: $0.displayName,
                snapshotURL: $0.snapshotURL,
                embeddingCount: $0.embeddings.count
            )
        }
    }

    func clear() {
        lock.lock()
        owners.removeAll()
        lock.unlock()
    }

    var isEmpty: Bool {
        lock.lock()
        defer { lock.unlock() }
        return owners.isEmpty
    }

    private func defaultDisplayName(for index: Int) -> String {
        "Owner \(index)"
    }
}
