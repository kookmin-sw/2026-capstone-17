//
//  trackStateMachine.swift
//  focus
//
//  Created by 이동언 on 3/7/26.
//

import Foundation

final class TrackStateMachine {
    private final class Entry {
        var embeddings: [[Float]] = []
        var label: TrackLabel? = nil
        var ownerID: UUID? = nil
        var frontFaceChecked = false
        var framesSeen = 0
        var wasAbsentLastFrame = false
    }

    private let ownerStore: OwnerEmbeddingStore
    private let classifier: OwnerOtherClassifier
    private var state: [Int: Entry] = [:]

    init(
        ownerStore: OwnerEmbeddingStore,
        classifier: OwnerOtherClassifier = OwnerOtherClassifier()
    ) {
        self.ownerStore = ownerStore
        self.classifier = classifier
    }

    func beginFrame(seenTrackIDs: Set<Int>) {
        for (trackID, entry) in state where !seenTrackIDs.contains(trackID) {
            entry.wasAbsentLastFrame = true
        }
    }

    func recordFrameSeen(trackID: Int) {
        let entry = state[trackID] ?? Entry()
        if entry.wasAbsentLastFrame {
            entry.embeddings.removeAll()
            entry.label = nil
            entry.ownerID = nil
            entry.frontFaceChecked = false
            entry.framesSeen = 0
            entry.wasAbsentLastFrame = false
        }
        entry.framesSeen += 1
        state[trackID] = entry
    }

    func applyState(to track: inout TrackedFace) {
        guard let entry = state[track.trackID] else {
            track.label = .pending
            track.ownerID = nil
            track.frontalEmbeddingSamples.removeAll()
            track.hasRetriedOther = false
            track.framesSeen = 0
            return
        }

        switch entry.label {
        case .owner:
            track.label = .owner
            track.ownerID = entry.ownerID
        case .other:
            track.label = .other
            track.ownerID = nil
        case .pending, nil:
            track.label = .pending
            track.ownerID = nil
        }

        track.frontalEmbeddingSamples = entry.embeddings
        track.hasRetriedOther = entry.frontFaceChecked
        track.framesSeen = entry.framesSeen
    }

    func label(for trackID: Int) -> TrackLabel? {
        state[trackID]?.label
    }

    func ownerID(for trackID: Int) -> UUID? {
        state[trackID]?.ownerID
    }

    func embeddings(for trackID: Int) -> [[Float]] {
        state[trackID]?.embeddings ?? []
    }

    func frontFaceChecked(for trackID: Int) -> Bool {
        state[trackID]?.frontFaceChecked ?? false
    }

    func framesSeen(for trackID: Int) -> Int {
        state[trackID]?.framesSeen ?? 0
    }

    func needsEmbeddingThisFrame(trackID: Int, isFrontal: Bool) -> Bool {
        let entry = state[trackID]
        switch entry?.label {
        case nil, .some(.pending):
            return (entry?.framesSeen ?? 0) > FocusConstants.skipFrames &&
                (entry?.embeddings.count ?? 0) < FocusConstants.collectFrames &&
                isFrontal
        case .other:
            return !(entry?.frontFaceChecked ?? false) && isFrontal
        case .owner:
            return false
        }
    }

    func addEmbedding(trackID: Int, embedding: [Float]) {
        let entry = state[trackID] ?? Entry()
        state[trackID] = entry

        guard entry.label == nil else { return }

        entry.embeddings.append(embedding)

        guard entry.embeddings.count >= FocusConstants.collectFrames else {
            return
        }

        let result = classifier.classify(
            embeddings: entry.embeddings,
            using: ownerStore
        )
        entry.label = result.label
        entry.ownerID = result.ownerID
    }

    @discardableResult
    func recheckFrontal(trackID: Int, embedding: [Float]) -> Bool {
        guard let entry = state[trackID],
              entry.label == .other,
              !entry.frontFaceChecked else {
            return false
        }

        let result = classifier.classify(embedding: embedding, using: ownerStore)
        entry.frontFaceChecked = true
        if result.label == .owner {
            entry.label = .owner
            entry.ownerID = result.ownerID
            return true
        }
        return false
    }

    func shouldCollectEmbedding(for track: TrackedFace) -> Bool {
        needsEmbeddingThisFrame(trackID: track.trackID, isFrontal: true)
    }

    func shouldRetryOther(for track: TrackedFace, isFrontal: Bool) -> Bool {
        guard track.label == .other else { return false }
        return needsEmbeddingThisFrame(trackID: track.trackID, isFrontal: isFrontal)
    }

    func updateLabel(
        track: inout TrackedFace,
        newEmbedding: [Float],
        isFrontal: Bool
    ) {
        guard isFrontal else { return }
        let entry = state[track.trackID] ?? Entry()
        state[track.trackID] = entry

        switch track.label {
        case .owner:
            return

        case .pending:
            addEmbedding(trackID: track.trackID, embedding: newEmbedding)
            applyState(to: &track)

        case .other:
            _ = recheckFrontal(trackID: track.trackID, embedding: newEmbedding)
            applyState(to: &track)
        }
    }

    func removeTrack(_ trackID: Int) {
        state.removeValue(forKey: trackID)
    }

    func removeOwner(ownerID: UUID) {
        state = state.filter { _, entry in
            entry.ownerID != ownerID
        }
    }

    func clear() {
        state.removeAll()
    }

    // MARK: - Private
}
