//
//  previewTrackHitTester.swift
//  focus
//
//  Created by Codex on 4/7/26.
//

import CoreGraphics

struct PreviewTrackHitTester {
    func containingTrackID(
        at point: CGPoint,
        previewSize: CGSize,
        tracks: [TrackedFace],
        sourceSize: CGSize,
        isMirrored: Bool,
        rotationDegrees: Int = 270
    ) -> Int? {
        mappedTrackRects(
            tracks: tracks,
            previewSize: previewSize,
            sourceSize: sourceSize,
            isMirrored: isMirrored,
            rotationDegrees: rotationDegrees
        )
        .filter { $0.rect.contains(point) }
        .min { lhs, rhs in
            let lhsArea = lhs.rect.width * lhs.rect.height
            let rhsArea = rhs.rect.width * rhs.rect.height
            if lhsArea == rhsArea {
                return lhs.distance(to: point) < rhs.distance(to: point)
            }
            return lhsArea < rhsArea
        }?
        .trackID
    }

    func nearestTrackID(
        to point: CGPoint,
        previewSize: CGSize,
        tracks: [TrackedFace],
        sourceSize: CGSize,
        isMirrored: Bool,
        rotationDegrees: Int = 270
    ) -> Int? {
        let mappedTracks = mappedTrackRects(
            tracks: tracks,
            previewSize: previewSize,
            sourceSize: sourceSize,
            isMirrored: isMirrored,
            rotationDegrees: rotationDegrees
        )

        guard let bestMatch = mappedTracks.min(by: { lhs, rhs in
            lhs.distance(to: point) < rhs.distance(to: point)
        }) else {
            return nil
        }

        let acceptanceDistance = max(56.0, min(bestMatch.rect.width, bestMatch.rect.height) * 0.7)
        guard bestMatch.distance(to: point) <= acceptanceDistance else {
            return nil
        }

        return bestMatch.trackID
    }

    func mappedTrackRects(
        tracks: [TrackedFace],
        previewSize: CGSize,
        sourceSize: CGSize,
        isMirrored: Bool,
        rotationDegrees: Int = 270
    ) -> [PreviewTrackRect] {
        guard sourceSize.width > 0,
              sourceSize.height > 0,
              previewSize.width > 0,
              previewSize.height > 0 else {
            return []
        }

        return tracks.compactMap { track in
            guard let rect = mapRectToPreview(
                track.bbox,
                previewSize: previewSize,
                sourceSize: sourceSize,
                isMirrored: isMirrored,
                rotationDegrees: rotationDegrees
            ) else {
                return nil
            }

            return PreviewTrackRect(trackID: track.trackID, rect: rect)
        }
    }

    func mapRectToPreview(
        _ rect: CGRect,
        previewSize: CGSize,
        sourceSize: CGSize,
        isMirrored: Bool,
        rotationDegrees: Int = 270
    ) -> CGRect? {
        let mappedOrigin = mapPointToPreview(
            rect.origin,
            previewSize: previewSize,
            sourceSize: sourceSize,
            isMirrored: isMirrored,
            rotationDegrees: rotationDegrees
        )
        let mappedMax = mapPointToPreview(
            CGPoint(x: rect.maxX, y: rect.maxY),
            previewSize: previewSize,
            sourceSize: sourceSize,
            isMirrored: isMirrored,
            rotationDegrees: rotationDegrees
        )

        let mappedRect = CGRect(
            x: min(mappedOrigin.x, mappedMax.x),
            y: min(mappedOrigin.y, mappedMax.y),
            width: abs(mappedMax.x - mappedOrigin.x),
            height: abs(mappedMax.y - mappedOrigin.y)
        ).intersection(CGRect(origin: .zero, size: previewSize))

        guard !mappedRect.isNull,
              !mappedRect.isEmpty,
              mappedRect.width > 0,
              mappedRect.height > 0 else {
            return nil
        }

        return mappedRect
    }

    private func mapPointToPreview(
        _ point: CGPoint,
        previewSize: CGSize,
        sourceSize: CGSize,
        isMirrored: Bool,
        rotationDegrees: Int
    ) -> CGPoint {
        let rotatedSize = rotatedSourceSize(for: sourceSize, rotationDegrees: rotationDegrees)
        let scale = max(
            previewSize.width / rotatedSize.width,
            previewSize.height / rotatedSize.height
        )
        let scaledSize = CGSize(
            width: rotatedSize.width * scale,
            height: rotatedSize.height * scale
        )
        let offset = CGPoint(
            x: (previewSize.width - scaledSize.width) / 2.0,
            y: (previewSize.height - scaledSize.height) / 2.0
        )

        let normalizedSource = CGPoint(
            x: point.x / sourceSize.width,
            y: point.y / sourceSize.height
        )
        let normalizedMirrored = CGPoint(
            x: isMirrored ? (1.0 - normalizedSource.x) : normalizedSource.x,
            y: normalizedSource.y
        )
        let normalizedRotated = rotate(normalizedMirrored, degrees: rotationDegrees)

        let rotatedPoint = CGPoint(
            x: normalizedRotated.x * rotatedSize.width,
            y: normalizedRotated.y * rotatedSize.height
        )

        return CGPoint(
            x: offset.x + rotatedPoint.x * scale,
            y: offset.y + rotatedPoint.y * scale
        )
    }

    private func rotatedSourceSize(for sourceSize: CGSize, rotationDegrees: Int) -> CGSize {
        switch normalizedRotationDegrees(rotationDegrees) {
        case 90, 270:
            return CGSize(width: sourceSize.height, height: sourceSize.width)
        default:
            return sourceSize
        }
    }

    private func rotate(_ point: CGPoint, degrees: Int) -> CGPoint {
        switch normalizedRotationDegrees(degrees) {
        case 90:
            return CGPoint(x: 1.0 - point.y, y: point.x)
        case 180:
            return CGPoint(x: 1.0 - point.x, y: 1.0 - point.y)
        case 270:
            return CGPoint(x: point.y, y: 1.0 - point.x)
        default:
            return point
        }
    }

    private func normalizedRotationDegrees(_ degrees: Int) -> Int {
        ((degrees % 360) + 360) % 360
    }
}

struct PreviewTrackRect: Equatable {
    let trackID: Int
    let rect: CGRect

    func distance(to point: CGPoint) -> CGFloat {
        if rect.contains(point) {
            return 0
        }

        let dx: CGFloat
        if point.x < rect.minX {
            dx = rect.minX - point.x
        } else if point.x > rect.maxX {
            dx = point.x - rect.maxX
        } else {
            dx = 0
        }

        let dy: CGFloat
        if point.y < rect.minY {
            dy = rect.minY - point.y
        } else if point.y > rect.maxY {
            dy = point.y - rect.maxY
        } else {
            dy = 0
        }

        return sqrt(dx * dx + dy * dy)
    }
}
