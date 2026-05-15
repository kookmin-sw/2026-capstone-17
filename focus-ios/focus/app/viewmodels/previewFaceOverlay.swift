//
//  previewFaceOverlay.swift
//  focus
//
//  Created by Codex on 4/11/26.
//

import Foundation
import CoreGraphics

struct PreviewFaceOverlay: Identifiable, Equatable {
    let trackID: Int
    let rect: CGRect
    let label: TrackLabel

    var id: Int { trackID }
}

enum PreviewDebugOverlayKind: Equatable {
    case detector
    case tracker
    case mask
}

struct PreviewDebugOverlay: Identifiable, Equatable {
    let id: String
    let rect: CGRect
    let kind: PreviewDebugOverlayKind
    let title: String
}
