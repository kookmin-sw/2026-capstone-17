//
//  trackTypes.swift
//  focus
//
//  Created by 이동언 on 3/7/26.
//

import Foundation
import CoreGraphics

struct TrackMatchCandidate: Equatable {
    let trackIndex: Int
    let detectionIndex: Int
    let cost: Float
    let iouDistance: Float
    let cosineDistance: Float
}

struct TrackAssignmentResult {
    let matches: [(trackIndex: Int, detectionIndex: Int)]
    let unmatchedTrackIndices: [Int]
    let unmatchedDetectionIndices: [Int]
}

enum TrackCost {
    static func intersectionOverUnion(_ a: CGRect, _ b: CGRect) -> Float {
        let inter = a.intersection(b)
        if inter.isNull || inter.isEmpty { return 0 }

        let interArea = inter.width * inter.height
        let unionArea = a.width * a.height + b.width * b.height - interArea
        guard unionArea > 0 else { return 0 }
        return Float(interArea / unionArea)
    }

    static func cosineSimilarity(_ a: [Float], _ b: [Float]) -> Float {
        guard a.count == b.count, !a.isEmpty else { return 0 }

        var dot: Float = 0
        var normA: Float = 0
        var normB: Float = 0

        for i in 0..<a.count {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        let denom = sqrt(normA) * sqrt(normB)
        guard denom > 0 else { return 0 }
        return dot / denom
    }

    static func combinedCost(
        track: TrackedFace,
        detection: DetectedFace,
        detectionTDMM: TDMMCoefficients?
    ) -> TrackMatchCandidate? {
        let iou = intersectionOverUnion(track.bbox, detection.bbox)
        let iouDistance = 1 - iou

        guard iouDistance <= FocusConstants.maxIouDistance else {
            return nil
        }

        var cosineDistance: Float = 0
        var shouldValidateCosineDistance = false

        if let trackTDMM = track.tdmm, let detectionTDMM {
            let similarity = cosineSimilarity(trackTDMM.idVector, detectionTDMM.idVector)
            cosineDistance = 1 - similarity
            shouldValidateCosineDistance = true
        }

        let cosineThreshold: Float
        if iou > 0.5 {
            cosineThreshold = FocusConstants.maxCosineDistance * FocusConstants.maxCosineRelaxedMultiplier
        } else {
            cosineThreshold = FocusConstants.maxCosineDistance
        }

        if shouldValidateCosineDistance && cosineDistance > cosineThreshold {
            return nil
        }

        let cost =
            FocusConstants.trackIouWeight * iouDistance +
            FocusConstants.trackCosineWeight * cosineDistance

        return TrackMatchCandidate(
            trackIndex: -1,
            detectionIndex: -1,
            cost: cost,
            iouDistance: iouDistance,
            cosineDistance: cosineDistance
        )
    }
}

enum DuplicateFaceFilter {
    static func dedupeDetections(_ detections: [DetectedFace]) -> [DetectedFace] {
        dedupe(items: detections, areDuplicates: { lhs, rhs in
            areLikelyDuplicateRects(lhs.bbox, rhs.bbox)
        }, isPreferred: { lhs, rhs in
            if lhs.confidence != rhs.confidence {
                return lhs.confidence > rhs.confidence
            }
            return lhs.bbox.width * lhs.bbox.height >= rhs.bbox.width * rhs.bbox.height
        })
    }

    static func dedupeTracks(_ tracks: [TrackedFace]) -> [TrackedFace] {
        dedupe(items: tracks, areDuplicates: { lhs, rhs in
            areLikelyDuplicateRects(lhs.bbox, rhs.bbox)
        }, isPreferred: { lhs, rhs in
            let lhsLabelPriority = trackLabelPriority(lhs.label)
            let rhsLabelPriority = trackLabelPriority(rhs.label)
            if lhsLabelPriority != rhsLabelPriority {
                return lhsLabelPriority > rhsLabelPriority
            }

            if lhs.ownerID != nil || rhs.ownerID != nil, lhs.ownerID != rhs.ownerID {
                return lhs.ownerID != nil
            }

            if lhs.missedFrames != rhs.missedFrames {
                return lhs.missedFrames < rhs.missedFrames
            }

            if lhs.framesSeen != rhs.framesSeen {
                return lhs.framesSeen > rhs.framesSeen
            }

            if lhs.age != rhs.age {
                return lhs.age > rhs.age
            }

            let lhsArea = lhs.bbox.width * lhs.bbox.height
            let rhsArea = rhs.bbox.width * rhs.bbox.height
            if lhsArea != rhsArea {
                return lhsArea > rhsArea
            }

            return lhs.trackID < rhs.trackID
        })
    }

    private static func dedupe<T>(
        items: [T],
        areDuplicates: (T, T) -> Bool,
        isPreferred: (T, T) -> Bool
    ) -> [T] {
        guard items.count > 1 else { return items }

        var kept: [T] = []
        for item in items {
            var shouldAppend = true

            for index in kept.indices {
                guard areDuplicates(item, kept[index]) else { continue }
                if isPreferred(item, kept[index]) {
                    kept[index] = item
                }
                shouldAppend = false
                break
            }

            if shouldAppend {
                kept.append(item)
            }
        }

        return kept
    }

    private static func areLikelyDuplicateRects(_ lhs: CGRect, _ rhs: CGRect) -> Bool {
        let iou = CGFloat(TrackCost.intersectionOverUnion(lhs, rhs))
        if iou >= 0.55 {
            return true
        }

        let lhsCenter = CGPoint(x: lhs.midX, y: lhs.midY)
        let rhsCenter = CGPoint(x: rhs.midX, y: rhs.midY)
        let dx = lhsCenter.x - rhsCenter.x
        let dy = lhsCenter.y - rhsCenter.y
        let centerDistance = sqrt(dx * dx + dy * dy)
        let minSide = max(1, min(lhs.width, lhs.height, rhs.width, rhs.height))

        return iou >= 0.35 && centerDistance <= minSide * 0.28
    }

    private static func trackLabelPriority(_ label: TrackLabel) -> Int {
        switch label {
        case .owner:
            return 2
        case .other:
            return 1
        case .pending:
            return 0
        }
    }
}
