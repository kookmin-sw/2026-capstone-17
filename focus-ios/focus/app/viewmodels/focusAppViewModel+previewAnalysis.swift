//
//  focusAppViewModel+previewAnalysis.swift
//  focus
//
//  Created by Codex on 4/11/26.
//

import Foundation

extension FocusAppViewModel {
    func activePreviewTracks() -> [TrackedFace] {
        previewTrackedFaces.filter { $0.missedFrames <= FocusConstants.previewOverlayMaxMissedFrames }
    }

    func overlayPreviewTracks() -> [TrackedFace] {
        DuplicateFaceFilter.dedupeTracks(activePreviewTracks())
    }
}
