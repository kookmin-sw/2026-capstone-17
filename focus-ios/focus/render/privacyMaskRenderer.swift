//
//  privacyMaskRenderer.swift
//  focus
//
//  Created by 이동언 on 3/7/26.
//

import Foundation
import CoreImage
import CoreGraphics
import CoreVideo

final class PrivacyMaskRenderer {
    private let ciContext = CIContext(options: nil)
    
    private struct EllipseMaskParameters {
        let center: CGPoint
        let radiusX: CGFloat
        let radiusY: CGFloat
        let angleRadians: CGFloat
    }

    func renderMasks(on pixelBuffer: CVPixelBuffer, tracks: [TrackedFace]) {
        let sourceImage = CIImage(cvPixelBuffer: pixelBuffer)
        let maskedImage = applyMasks(to: sourceImage, tracks: tracks)
        ciContext.render(maskedImage, to: pixelBuffer)
    }

    func makeMaskedPixelBuffer(from pixelBuffer: CVPixelBuffer, tracks: [TrackedFace]) -> CVPixelBuffer? {
        let sourceImage = CIImage(cvPixelBuffer: pixelBuffer)
        let maskedImage = applyMasks(to: sourceImage, tracks: tracks)
        return render(image: maskedImage, matching: pixelBuffer)
    }

    func copyPixelBuffer(_ pixelBuffer: CVPixelBuffer) -> CVPixelBuffer? {
        let sourceImage = CIImage(cvPixelBuffer: pixelBuffer)
        return render(image: sourceImage, matching: pixelBuffer)
    }

    static func debugMaskRect(for track: TrackedFace) -> CGRect {
        expandedFaceRect(from: track.bbox)
    }

    func applyMasks(to image: CIImage, tracks: [TrackedFace]) -> CIImage {
        let facesToMask = tracks
            .filter { shouldMask(track: $0) }
            .prefix(FocusConstants.maxSimultaneousMaskFaces)

        var output = image

        for track in facesToMask {
            output = applyBBoxFallbackMask(to: output, track: track)
        }

        return output
    }

    private func shouldMask(track: TrackedFace) -> Bool {
        switch track.label {
        case .owner:
            return false
        case .pending, .other:
            return true
        }
    }

    private func applyLandmarkAwareMask(to image: CIImage, track: TrackedFace) -> CIImage {
        guard track.missedFrames == 0 else {
            return applyBBoxFallbackMask(to: image, track: track)
        }
        guard let landmarks = track.landmarks else {
            return applyBBoxFallbackMask(to: image, track: track)
        }
        guard hasStableLandmarkGeometry(for: track, landmarks: landmarks) else {
            return applyBBoxFallbackMask(to: image, track: track)
        }
        guard !isNearImageEdge(track.bbox, within: image.extent) else {
            return applyBBoxFallbackMask(to: image, track: track)
        }

        let ellipse = ellipseParameters(for: track, landmarks: landmarks)
        let faceRect = ellipseBounds(for: ellipse).intersection(image.extent)
        let maskImage = makeEllipticalMask(
            extent: image.extent,
            ellipse: ellipse
        )

        let pixelated = pixelatedImage(from: image, faceRect: faceRect)
        return blend(pixelated: pixelated, original: image, mask: maskImage)
    }

    private func applyBBoxFallbackMask(to image: CIImage, track: TrackedFace) -> CIImage {
        let captureRect = Self.expandedFaceRect(from: track.bbox)
        let imageRect = Self.imageSpaceRect(fromCaptureRect: captureRect, extent: image.extent)
        let mask = solidRectMask(extent: image.extent, rect: imageRect)
        let pixelated = pixelatedImage(from: image, faceRect: imageRect)
        return blend(pixelated: pixelated, original: image, mask: mask)
    }

    private static func expandedFaceRect(from rect: CGRect) -> CGRect {
        let expandLeft = rect.width * 0.025
        let expandRight = rect.width * 0.025
        let expandTop = rect.height * 0.015
        let expandBottom = rect.height * 0.035

        return CGRect(
            x: rect.origin.x - expandLeft,
            y: rect.origin.y - expandTop,
            width: rect.width + expandLeft + expandRight,
            height: rect.height + expandTop + expandBottom
        )
    }

    private static func imageSpaceRect(fromCaptureRect rect: CGRect, extent: CGRect) -> CGRect {
        CGRect(
            x: extent.minX + rect.origin.x,
            y: extent.maxY - rect.maxY,
            width: rect.width,
            height: rect.height
        ).intersection(extent)
    }

    private func pixelatedImage(from image: CIImage, faceRect: CGRect) -> CIImage {
        guard let filter = CIFilter(name: "CIPixellate") else {
            return image
        }

        filter.setValue(image, forKey: kCIInputImageKey)
        filter.setValue(CIVector(x: faceRect.midX, y: faceRect.midY), forKey: kCIInputCenterKey)
        filter.setValue(FocusConstants.mosaicBlockSize, forKey: kCIInputScaleKey)

        return filter.outputImage?.cropped(to: image.extent) ?? image
    }

    private func makeEllipticalMask(
        extent: CGRect,
        ellipse: EllipseMaskParameters
    ) -> CIImage {
        let width = max(Int(ceil(extent.width)), 1)
        let height = max(Int(ceil(extent.height)), 1)

        guard let context = CGContext(
            data: nil,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: 0,
            space: CGColorSpaceCreateDeviceGray(),
            bitmapInfo: CGImageAlphaInfo.none.rawValue
        ) else {
            return solidRectMask(extent: extent, rect: ellipseBounds(for: ellipse))
        }

        context.setFillColor(gray: 0, alpha: 1)
        context.fill(CGRect(origin: .zero, size: CGSize(width: width, height: height)))

        context.setFillColor(gray: 1, alpha: 1)
        context.translateBy(
            x: ellipse.center.x - extent.origin.x,
            y: ellipse.center.y - extent.origin.y
        )
        context.rotate(by: ellipse.angleRadians)
        context.fillEllipse(
            in: CGRect(
                x: -ellipse.radiusX,
                y: -ellipse.radiusY,
                width: ellipse.radiusX * 2,
                height: ellipse.radiusY * 2
            )
        )

        guard let maskCGImage = context.makeImage() else {
            return solidRectMask(extent: extent, rect: ellipseBounds(for: ellipse))
        }

        return CIImage(cgImage: maskCGImage).cropped(to: extent)
    }

    private func ellipseParameters(
        for track: TrackedFace,
        landmarks: FaceLandmarks5,
        paddingRatio: CGFloat = 1.14
    ) -> EllipseMaskParameters {
        let eyeCenter = CGPoint(
            x: (landmarks.leftEye.x + landmarks.rightEye.x) / 2.0,
            y: (landmarks.leftEye.y + landmarks.rightEye.y) / 2.0
        )
        let mouthCenter = CGPoint(
            x: (landmarks.leftMouth.x + landmarks.rightMouth.x) / 2.0,
            y: (landmarks.leftMouth.y + landmarks.rightMouth.y) / 2.0
        )
        let eyeDistance = hypot(landmarks.leftEye.x - landmarks.rightEye.x, landmarks.leftEye.y - landmarks.rightEye.y)
        let eyeMouthDistance = hypot(mouthCenter.x - eyeCenter.x, mouthCenter.y - eyeCenter.y)
        let landmarkDrivenCenter = CGPoint(
            x: eyeCenter.x + ((mouthCenter.x - eyeCenter.x) * 0.42),
            y: eyeCenter.y + ((mouthCenter.y - eyeCenter.y) * 0.42)
        )
        let bboxCenter = CGPoint(x: track.bbox.midX, y: track.bbox.midY)
        let center = CGPoint(
            x: (landmarkDrivenCenter.x * 0.68) + (bboxCenter.x * 0.32),
            y: (landmarkDrivenCenter.y * 0.62) + (bboxCenter.y * 0.38) + (track.bbox.height * 0.03)
        )
        let angleRadians = atan2(
            landmarks.leftEye.y - landmarks.rightEye.y,
            landmarks.leftEye.x - landmarks.rightEye.x
        )
        let radiusX = max(eyeDistance * paddingRatio, track.bbox.width * 0.58)
        let radiusY = max(eyeMouthDistance * 1.55 * paddingRatio, track.bbox.height * 0.70)

        return EllipseMaskParameters(
            center: center,
            radiusX: radiusX,
            radiusY: radiusY,
            angleRadians: angleRadians
        )
    }

    private func ellipseBounds(for ellipse: EllipseMaskParameters) -> CGRect {
        let cosine = cos(ellipse.angleRadians)
        let sine = sin(ellipse.angleRadians)
        let ux = ellipse.radiusX * cosine
        let uy = ellipse.radiusX * sine
        let vx = ellipse.radiusY * -sine
        let vy = ellipse.radiusY * cosine
        let halfWidth = sqrt((ux * ux) + (vx * vx))
        let halfHeight = sqrt((uy * uy) + (vy * vy))

        return CGRect(
            x: ellipse.center.x - halfWidth,
            y: ellipse.center.y - halfHeight,
            width: halfWidth * 2,
            height: halfHeight * 2
        )
    }

    private func hasStableLandmarkGeometry(
        for track: TrackedFace,
        landmarks: FaceLandmarks5
    ) -> Bool {
        let bbox = track.bbox
        guard bbox.width > 0, bbox.height > 0 else {
            return false
        }

        let paddedBBox = bbox.insetBy(dx: -bbox.width * 0.10, dy: -bbox.height * 0.10)
        let allPoints = [
            landmarks.leftEye,
            landmarks.rightEye,
            landmarks.nose,
            landmarks.leftMouth,
            landmarks.rightMouth
        ]

        guard allPoints.allSatisfy({ paddedBBox.contains($0) }) else {
            return false
        }

        let eyeCenter = CGPoint(
            x: (landmarks.leftEye.x + landmarks.rightEye.x) / 2.0,
            y: (landmarks.leftEye.y + landmarks.rightEye.y) / 2.0
        )
        let mouthCenter = CGPoint(
            x: (landmarks.leftMouth.x + landmarks.rightMouth.x) / 2.0,
            y: (landmarks.leftMouth.y + landmarks.rightMouth.y) / 2.0
        )
        let landmarkDrivenCenter = CGPoint(
            x: eyeCenter.x + ((mouthCenter.x - eyeCenter.x) * 0.42),
            y: eyeCenter.y + ((mouthCenter.y - eyeCenter.y) * 0.42)
        )
        let bboxCenter = CGPoint(x: bbox.midX, y: bbox.midY)
        let eyeDistance = hypot(
            landmarks.leftEye.x - landmarks.rightEye.x,
            landmarks.leftEye.y - landmarks.rightEye.y
        )
        let eyeMouthDistance = hypot(
            mouthCenter.x - eyeCenter.x,
            mouthCenter.y - eyeCenter.y
        )
        let mouthWidth = hypot(
            landmarks.leftMouth.x - landmarks.rightMouth.x,
            landmarks.leftMouth.y - landmarks.rightMouth.y
        )

        let maxCenterOffsetX = bbox.width * 0.26
        let maxCenterOffsetY = bbox.height * 0.24
        let centerOffsetX = abs(landmarkDrivenCenter.x - bboxCenter.x)
        let centerOffsetY = abs(landmarkDrivenCenter.y - bboxCenter.y)

        guard centerOffsetX <= maxCenterOffsetX,
              centerOffsetY <= maxCenterOffsetY else {
            return false
        }

        guard eyeDistance >= bbox.width * 0.12,
              eyeDistance <= bbox.width * 0.78 else {
            return false
        }

        guard eyeMouthDistance >= bbox.height * 0.16,
              eyeMouthDistance <= bbox.height * 0.72 else {
            return false
        }

        guard mouthWidth >= bbox.width * 0.10,
              mouthWidth <= bbox.width * 0.60 else {
            return false
        }

        let noseToCenterDistance = hypot(
            landmarks.nose.x - bboxCenter.x,
            landmarks.nose.y - bboxCenter.y
        )
        guard noseToCenterDistance <= max(bbox.width, bbox.height) * 0.28 else {
            return false
        }

        return true
    }

    private func isNearImageEdge(_ rect: CGRect, within extent: CGRect) -> Bool {
        let horizontalMargin = extent.width * 0.12
        let verticalMargin = extent.height * 0.12

        return rect.minX <= extent.minX + horizontalMargin ||
        rect.maxX >= extent.maxX - horizontalMargin ||
        rect.minY <= extent.minY + verticalMargin ||
        rect.maxY >= extent.maxY - verticalMargin
    }

    private func solidRectMask(extent: CGRect, rect: CGRect) -> CIImage {
        let color = CIImage(color: CIColor(red: 1, green: 1, blue: 1, alpha: 1))
            .cropped(to: rect)

        let clear = CIImage(color: .clear).cropped(to: extent)
        return color.composited(over: clear).cropped(to: extent)
    }

    private func blend(pixelated: CIImage, original: CIImage, mask: CIImage) -> CIImage {
        guard let blend = CIFilter(name: "CIBlendWithMask") else {
            return original
        }

        blend.setValue(pixelated, forKey: kCIInputImageKey)
        blend.setValue(original, forKey: kCIInputBackgroundImageKey)
        blend.setValue(mask, forKey: "inputMaskImage")

        return blend.outputImage ?? original
    }

    private func render(image: CIImage, matching sourcePixelBuffer: CVPixelBuffer) -> CVPixelBuffer? {
        guard let target = makePixelBuffer(matching: sourcePixelBuffer) else {
            return nil
        }

        ciContext.render(image, to: target)
        return target
    }

    private func makePixelBuffer(matching sourcePixelBuffer: CVPixelBuffer) -> CVPixelBuffer? {
        let width = CVPixelBufferGetWidth(sourcePixelBuffer)
        let height = CVPixelBufferGetHeight(sourcePixelBuffer)
        let pixelFormat = CVPixelBufferGetPixelFormatType(sourcePixelBuffer)

        let attributes: [String: Any] = [
            kCVPixelBufferPixelFormatTypeKey as String: Int(pixelFormat),
            kCVPixelBufferWidthKey as String: width,
            kCVPixelBufferHeightKey as String: height,
            kCVPixelBufferCGImageCompatibilityKey as String: true,
            kCVPixelBufferCGBitmapContextCompatibilityKey as String: true,
            kCVPixelBufferMetalCompatibilityKey as String: true,
            kCVPixelBufferIOSurfacePropertiesKey as String: [:]
        ]

        var pixelBuffer: CVPixelBuffer?
        let status = CVPixelBufferCreate(
            kCFAllocatorDefault,
            width,
            height,
            pixelFormat,
            attributes as CFDictionary,
            &pixelBuffer
        )

        guard status == kCVReturnSuccess else {
            return nil
        }

        return pixelBuffer
    }
}
