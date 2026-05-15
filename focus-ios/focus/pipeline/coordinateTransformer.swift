//
//  coordinateTransformer.swift
//  focus
//
//  Created by 이동언 on 3/7/26.
//

import Foundation
import CoreGraphics

struct CoordinateTransformConfig: Sendable {
    let sourceSize: CGSize
    let destinationSize: CGSize
    let isMirrored: Bool
    let rotationDegrees: Int

    init(
        sourceSize: CGSize,
        destinationSize: CGSize,
        isMirrored: Bool = false,
        rotationDegrees: Int = 0
    ) {
        self.sourceSize = sourceSize
        self.destinationSize = destinationSize
        self.isMirrored = isMirrored
        self.rotationDegrees = rotationDegrees
    }
}

final class CoordinateTransformer {
    func mapRectToPreview(_ rect: CGRect, config: CoordinateTransformConfig) -> CGRect {
        let mappedOrigin = mapPointToPreview(rect.origin, config: config)
        let mappedMax = mapPointToPreview(
            CGPoint(x: rect.maxX, y: rect.maxY),
            config: config
        )

        let x = min(mappedOrigin.x, mappedMax.x)
        let y = min(mappedOrigin.y, mappedMax.y)
        let width = abs(mappedMax.x - mappedOrigin.x)
        let height = abs(mappedMax.y - mappedOrigin.y)

        return CGRect(x: x, y: y, width: width, height: height)
    }

    func mapPointToPreview(_ point: CGPoint, config: CoordinateTransformConfig) -> CGPoint {
        guard config.sourceSize.width > 0,
              config.sourceSize.height > 0,
              config.destinationSize.width > 0,
              config.destinationSize.height > 0 else {
            return .zero
        }

        var normalized = CGPoint(
            x: point.x / config.sourceSize.width,
            y: point.y / config.sourceSize.height
        )

        if config.isMirrored {
            normalized.x = 1.0 - normalized.x
        }

        normalized = applyRotation(to: normalized, degrees: config.rotationDegrees)

        return CGPoint(
            x: normalized.x * config.destinationSize.width,
            y: normalized.y * config.destinationSize.height
        )
    }

    func clampRect(_ rect: CGRect, to size: CGSize) -> CGRect {
        let bounds = CGRect(origin: .zero, size: size)
        return rect.intersection(bounds)
    }

    private func applyRotation(to point: CGPoint, degrees: Int) -> CGPoint {
        switch ((degrees % 360) + 360) % 360 {
        case 90:
            return CGPoint(x: 1 - point.y, y: point.x)
        case 180:
            return CGPoint(x: 1 - point.x, y: 1 - point.y)
        case 270:
            return CGPoint(x: point.y, y: 1 - point.x)
        default:
            return point
        }
    }
}
