//
//  metadataPolicy.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation
import CoreGraphics

enum MetadataDropReason: String {
    case ownerNotAllowed = "owner_not_allowed"
    case pendingNotAllowed = "pending_not_allowed"
    case missingTDMM = "missing_tdmm"
    case invalidTDMMLayout = "invalid_tdmm_layout"
    case invalidBBox = "invalid_bbox"
}

struct MetadataPolicyResult {
    let shouldInclude: Bool
    let reason: MetadataDropReason?
}

enum MetadataPolicy {
    static func evaluate(track: TrackedFace) -> MetadataPolicyResult {
        switch track.label {
        case .owner:
            return MetadataPolicyResult(
                shouldInclude: false,
                reason: .ownerNotAllowed
            )

        case .pending:
            return MetadataPolicyResult(
                shouldInclude: false,
                reason: .pendingNotAllowed
            )

        case .other:
            break
        }

        guard isValidBBox(track.bbox) else {
            return MetadataPolicyResult(
                shouldInclude: false,
                reason: .invalidBBox
            )
        }

        guard let tdmm = track.tdmm else {
            return MetadataPolicyResult(
                shouldInclude: false,
                reason: .missingTDMM
            )
        }

        guard tdmm.isValidLayout else {
            return MetadataPolicyResult(
                shouldInclude: false,
                reason: .invalidTDMMLayout
            )
        }

        return MetadataPolicyResult(
            shouldInclude: true,
            reason: nil
        )
    }

    static func isValidBBox(_ rect: CGRect) -> Bool {
        guard rect.width > 0, rect.height > 0 else { return false }
        guard rect.origin.x.isFinite,
              rect.origin.y.isFinite,
              rect.width.isFinite,
              rect.height.isFinite else { return false }
        return true
    }

    static func clampBBoxToInt(_ rect: CGRect) -> (x: Int32, y: Int32, width: Int32, height: Int32) {
        let x = Int32(max(0, Int(rect.origin.x.rounded(.towardZero))))
        let y = Int32(max(0, Int(rect.origin.y.rounded(.towardZero))))
        let width = Int32(max(0, Int(rect.width.rounded(.towardZero))))
        let height = Int32(max(0, Int(rect.height.rounded(.towardZero))))
        return (x, y, width, height)
    }
}
