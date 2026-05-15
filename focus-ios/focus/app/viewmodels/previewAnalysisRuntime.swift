//
//  previewAnalysisRuntime.swift
//  focus
//
//  Created by Codex on 4/11/26.
//

import Foundation
import CoreVideo

final class PreviewAnalysisRuntime {
    let hitTester = PreviewTrackHitTester()
    let faceTracker = FaceTracker()
    let trackStateMachine: TrackStateMachine

    var latestPixelBuffer: CVPixelBuffer?

    init(ownerStore: OwnerEmbeddingStore, classifier: OwnerOtherClassifier) {
        trackStateMachine = TrackStateMachine(
            ownerStore: ownerStore,
            classifier: classifier
        )
    }

    func reset() {
        latestPixelBuffer = nil
        trackStateMachine.clear()
        faceTracker.reset()
    }
}
