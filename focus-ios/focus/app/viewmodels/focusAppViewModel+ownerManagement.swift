//
//  focusAppViewModel+ownerManagement.swift
//  focus
//
//  Created by Codex on 4/11/26.
//

import Foundation
import CoreGraphics

extension FocusAppViewModel {
    func previewTrackContainingPoint(
        to location: CGPoint,
        previewSize: CGSize,
        visibleTracks: [TrackedFace]
    ) -> TrackedFace? {
        guard let selectedTrackID = previewRuntime.hitTester.containingTrackID(
            at: location,
            previewSize: previewSize,
            tracks: visibleTracks,
            sourceSize: previewSourceSize,
            isMirrored: cameraFacing == .front
        ) else {
            return nil
        }

        return visibleTracks.first(where: { $0.trackID == selectedTrackID })
    }

    func markPreviewTracksAsOther(ownerID: UUID) {
        previewRuntime.trackStateMachine.removeOwner(ownerID: ownerID)
        previewTrackedFaces = previewTrackedFaces.map { track in
            guard track.ownerID == ownerID else { return track }
            return previewTrackMarkedAsOther(track)
        }
    }

    func previewTrackMarkedAsOther(_ track: TrackedFace) -> TrackedFace {
        var updatedTrack = track
        updatedTrack.label = .other
        updatedTrack.ownerID = nil
        updatedTrack.frontalEmbeddingSamples.removeAll()
        updatedTrack.hasRetriedOther = true
        return updatedTrack
    }

    func previewTrackMarkedAsOwner(_ track: TrackedFace, ownerID: UUID?) -> TrackedFace {
        var updatedTrack = track
        updatedTrack.label = .owner
        updatedTrack.ownerID = ownerID
        return updatedTrack
    }

    func saveRecordingToPhotoLibrary(_ recordingURL: URL) async {
        do {
            try await photoLibraryVideoSaver.saveVideo(at: recordingURL)
            showStatus("녹화 영상을 사진 보관함에 저장했어요.")
        } catch {
            handleError("녹화 영상 저장 실패: \(error.localizedDescription)")
        }
    }

    func showReportArchivePlaceholder() {
        showStatus("방송 회고록 기능은 준비 중입니다.")
    }

    func saveOriginalClipPlaceholder() {
        showStatus("원본클립 저장 기능은 준비 중입니다.")
    }

    func showStatus(_ message: String) {
        statusDismissTask?.cancel()
        transientStatusMessage = message

        statusDismissTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(1.8))
            guard !Task.isCancelled else { return }
            transientStatusMessage = nil
        }
    }

    func handleError(_ message: String) {
        pendingOwnerRegistrationFeedback = false
        pendingOwnerFeedbackTask?.cancel()
        errorMessage = message
        showErrorAlert = true
    }

    func sanitizedTracksForCurrentOwners(_ tracks: [TrackedFace]) -> [TrackedFace] {
        let validOwnerIDs = Set(ownerStore.allOwners().map { $0.id })
        guard !validOwnerIDs.isEmpty else {
            return tracks.map { track in
                guard track.label == .owner else { return track }
                return previewTrackMarkedAsOther(track)
            }
        }

        return tracks.map { track in
            guard track.label == .owner else { return track }
            guard let ownerID = track.ownerID, validOwnerIDs.contains(ownerID) else {
                return previewTrackMarkedAsOther(track)
            }
            return track
        }
    }
}
