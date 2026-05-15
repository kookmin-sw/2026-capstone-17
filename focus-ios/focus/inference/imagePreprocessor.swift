//
//  imagePreprocessor.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation
import CoreVideo
import CoreGraphics
import CoreImage
import UIKit

final class ImagePreprocessor {
    static let shared = ImagePreprocessor()

    private let ciContext = CIContext(options: nil)

    private init() {}

    // MARK: - Public

    /// short-side 기준으로 비율 유지 resize
    func resizeShortSideRGB(
        from pixelBuffer: CVPixelBuffer,
        shortSide: Int
    ) throws -> (tensor: ImageTensorData, meta: ResizeMeta) {
        let sourceWidth = CVPixelBufferGetWidth(pixelBuffer)
        let sourceHeight = CVPixelBufferGetHeight(pixelBuffer)

        guard sourceWidth > 0, sourceHeight > 0 else {
            throw InferenceError.preprocessingFailed("원본 pixelBuffer 크기가 올바르지 않습니다.")
        }

        let minSide = min(sourceWidth, sourceHeight)
        let scale = CGFloat(shortSide) / CGFloat(minSide)

        let resizedWidth = max(1, Int(round(CGFloat(sourceWidth) * scale)))
        let resizedHeight = max(1, Int(round(CGFloat(sourceHeight) * scale)))

        let rgbData = try renderRGBData(
            from: pixelBuffer,
            cropRect: CGRect(x: 0, y: 0, width: sourceWidth, height: sourceHeight),
            outputSize: CGSize(width: resizedWidth, height: resizedHeight)
        )

        let meta = ResizeMeta(
            originalWidth: sourceWidth,
            originalHeight: sourceHeight,
            resizedWidth: resizedWidth,
            resizedHeight: resizedHeight,
            scale: scale
        )

        return (
            ImageTensorData(data: rgbData, width: resizedWidth, height: resizedHeight, channels: 3),
            meta
        )
    }

    func cropRGB(
        from pixelBuffer: CVPixelBuffer,
        rect: CGRect,
        outputSize: CGSize
    ) throws -> ImageTensorData {
        let croppedImage = try makeCroppedCGImage(from: pixelBuffer, rect: rect)
        let rgbData = try renderRGBData(
            from: croppedImage,
            outputSize: outputSize
        )

        return ImageTensorData(
            data: rgbData,
            width: Int(outputSize.width),
            height: Int(outputSize.height),
            channels: 3
        )
    }

    func cropAlignedRGBForRecognition(
        from pixelBuffer: CVPixelBuffer,
        rect: CGRect,
        landmarks: FaceLandmarks5?,
        outputSize: CGSize,
        minimumAngleDegrees: CGFloat = FocusConstants.arcFaceMinAlignmentAngleDeg
    ) throws -> ImageTensorData {
        let croppedImage = try makeCroppedCGImage(from: pixelBuffer, rect: rect)
        let alignedImage = rotateRecognitionCropIfNeeded(
            croppedImage,
            landmarks: landmarks,
            minimumAngleDegrees: minimumAngleDegrees
        )

        let rgbData = try renderRGBData(
            from: alignedImage,
            outputSize: outputSize
        )

        return ImageTensorData(
            data: rgbData,
            width: Int(outputSize.width),
            height: Int(outputSize.height),
            channels: 3
        )
    }

    /// HWC UInt8 RGB -> Float32 NHWC [0,1]
    func uint8RGBToFloatNHWC(_ image: ImageTensorData, scale: Float = 1.0 / 255.0) -> Data {
        let bytes = [UInt8](image.data)
        var floats = [Float]()
        floats.reserveCapacity(bytes.count)
        for b in bytes {
            floats.append(Float(b) * scale)
        }
        return floats.withUnsafeBufferPointer { Data(buffer: $0) }
    }

    /// HWC UInt8 RGB -> Float32 NCHW with custom normalize
    func uint8RGBToFloatNCHW(
        _ image: ImageTensorData,
        normalize: (Float) -> Float
    ) -> Data {
        let bytes = [UInt8](image.data)
        let hw = image.width * image.height

        var floats = Array(repeating: Float(0), count: hw * 3)

        for i in 0..<hw {
            let base = i * 3
            let r = normalize(Float(bytes[base]))
            let g = normalize(Float(bytes[base + 1]))
            let b = normalize(Float(bytes[base + 2]))

            floats[i] = r
            floats[hw + i] = g
            floats[2 * hw + i] = b
        }

        return floats.withUnsafeBufferPointer { Data(buffer: $0) }
    }

    func mapRectToOriginal(_ rect: CGRect, using meta: ResizeMeta) -> CGRect {
        let invScale = 1.0 / meta.scale
        return CGRect(
            x: rect.origin.x * invScale,
            y: rect.origin.y * invScale,
            width: rect.width * invScale,
            height: rect.height * invScale
        )
    }

    func mapPointToOriginal(_ point: CGPoint, using meta: ResizeMeta) -> CGPoint {
        let invScale = 1.0 / meta.scale
        return CGPoint(x: point.x * invScale, y: point.y * invScale)
    }

    func clampRectToOriginalBounds(_ rect: CGRect, width: Int, height: Int) -> CGRect {
        let bounds = CGRect(x: 0, y: 0, width: width, height: height)
        return rect.intersection(bounds)
    }

    func l2Normalize(_ vector: [Float]) -> [Float] {
        guard !vector.isEmpty else { return vector }
        let norm = sqrt(vector.reduce(0) { $0 + $1 * $1 })
        guard norm > 0 else { return vector }
        return vector.map { $0 / norm }
    }

    func jpegData(
        from pixelBuffer: CVPixelBuffer,
        rect: CGRect,
        compressionQuality: CGFloat = 0.85,
        rotationDegrees: CGFloat = 0
    ) throws -> Data {
        let sourceWidth = CVPixelBufferGetWidth(pixelBuffer)
        let sourceHeight = CVPixelBufferGetHeight(pixelBuffer)

        let clamped = rect.intersection(CGRect(x: 0, y: 0, width: sourceWidth, height: sourceHeight))
        guard !clamped.isNull, !clamped.isEmpty, clamped.width >= 1, clamped.height >= 1 else {
            throw InferenceError.cropTooSmall
        }

        let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
        let cropped = ciImage.cropped(to: clamped)

        guard let cgImage = ciContext.createCGImage(cropped, from: cropped.extent) else {
            throw InferenceError.preprocessingFailed("snapshot CGImage 생성 실패")
        }

        let imageForEncoding = rotateSnapshotImageIfNeeded(
            cgImage,
            degrees: rotationDegrees
        )

        guard let data = imageForEncoding.jpegData(compressionQuality: compressionQuality) else {
            throw InferenceError.preprocessingFailed("snapshot JPEG 인코딩 실패")
        }

        return data
    }

    // MARK: - Private

    private func renderRGBData(
        from pixelBuffer: CVPixelBuffer,
        cropRect: CGRect,
        outputSize: CGSize
    ) throws -> Data {
        let cgImage = try makeCroppedCGImage(from: pixelBuffer, rect: cropRect)
        return try renderRGBData(from: cgImage, outputSize: outputSize)
    }

    private func renderRGBData(
        from cgImage: CGImage,
        outputSize: CGSize
    ) throws -> Data {
        let width = Int(outputSize.width)
        let height = Int(outputSize.height)
        guard width > 0, height > 0 else {
            throw InferenceError.preprocessingFailed("출력 크기가 올바르지 않습니다.")
        }

        let bytesPerPixel = 4
        let bytesPerRow = width * bytesPerPixel
        let bitsPerComponent = 8

        var rgba = [UInt8](repeating: 0, count: width * height * 4)

        guard let context = CGContext(
            data: &rgba,
            width: width,
            height: height,
            bitsPerComponent: bitsPerComponent,
            bytesPerRow: bytesPerRow,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else {
            throw InferenceError.preprocessingFailed("CGContext 생성 실패")
        }

        context.interpolationQuality = .high
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))

        var rgb = [UInt8]()
        rgb.reserveCapacity(width * height * 3)

        for i in stride(from: 0, to: rgba.count, by: 4) {
            rgb.append(rgba[i])     // R
            rgb.append(rgba[i + 1]) // G
            rgb.append(rgba[i + 2]) // B
        }

        return Data(rgb)
    }

    private func makeCroppedCGImage(
        from pixelBuffer: CVPixelBuffer,
        rect: CGRect
    ) throws -> CGImage {
        let sourceWidth = CVPixelBufferGetWidth(pixelBuffer)
        let sourceHeight = CVPixelBufferGetHeight(pixelBuffer)
        let clamped = rect.intersection(CGRect(x: 0, y: 0, width: sourceWidth, height: sourceHeight))
        guard !clamped.isNull, !clamped.isEmpty, clamped.width >= 1, clamped.height >= 1 else {
            throw InferenceError.cropTooSmall
        }

        let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
        let cropped = ciImage.cropped(to: clamped)
        guard let cgImage = ciContext.createCGImage(cropped, from: cropped.extent) else {
            throw InferenceError.preprocessingFailed("CGImage 생성 실패")
        }

        return cgImage
    }

    private func rotateRecognitionCropIfNeeded(
        _ cgImage: CGImage,
        landmarks: FaceLandmarks5?,
        minimumAngleDegrees: CGFloat
    ) -> CGImage {
        guard let landmarks else { return cgImage }

        let angleRadians = recognitionAngleRadians(for: landmarks)
        let angleDegrees = angleRadians * 180.0 / .pi
        guard abs(angleDegrees) >= minimumAngleDegrees else {
            return cgImage
        }

        let size = CGSize(width: cgImage.width, height: cgImage.height)
        let renderer = UIGraphicsImageRenderer(size: size)
        let sourceImage = UIImage(cgImage: cgImage)

        let alignedImage = renderer.image { context in
            UIColor.black.setFill()
            context.fill(CGRect(origin: .zero, size: size))

            let cgContext = context.cgContext
            cgContext.interpolationQuality = .high
            cgContext.translateBy(x: size.width / 2.0, y: size.height / 2.0)
            cgContext.rotate(by: -angleRadians)

            sourceImage.draw(
                in: CGRect(
                    x: -size.width / 2.0,
                    y: -size.height / 2.0,
                    width: size.width,
                    height: size.height
                )
            )
        }

        return alignedImage.cgImage ?? cgImage
    }

    private func recognitionAngleRadians(for landmarks: FaceLandmarks5) -> CGFloat {
        let dx = landmarks.leftEye.x - landmarks.rightEye.x
        let dy = landmarks.leftEye.y - landmarks.rightEye.y
        return atan2(dy, dx)
    }

    private func rotateSnapshotImageIfNeeded(
        _ cgImage: CGImage,
        degrees: CGFloat
    ) -> UIImage {
        let normalizedDegrees = normalizedRotationDegrees(degrees)
        guard abs(normalizedDegrees) > 0.01 else {
            return UIImage(cgImage: cgImage)
        }

        let sourceSize = CGSize(width: cgImage.width, height: cgImage.height)
        let radians = normalizedDegrees * .pi / 180.0
        let rotatedBounds = CGRect(origin: .zero, size: sourceSize)
            .applying(CGAffineTransform(rotationAngle: radians))

        let canvasSize = CGSize(
            width: max(1, ceil(abs(rotatedBounds.width))),
            height: max(1, ceil(abs(rotatedBounds.height)))
        )

        let renderer = UIGraphicsImageRenderer(size: canvasSize)
        let sourceImage = UIImage(cgImage: cgImage)

        return renderer.image { context in
            UIColor.black.setFill()
            context.fill(CGRect(origin: .zero, size: canvasSize))

            let cgContext = context.cgContext
            cgContext.interpolationQuality = .high
            cgContext.translateBy(
                x: canvasSize.width / 2.0,
                y: canvasSize.height / 2.0
            )
            cgContext.rotate(by: radians)

            sourceImage.draw(
                in: CGRect(
                    x: -sourceSize.width / 2.0,
                    y: -sourceSize.height / 2.0,
                    width: sourceSize.width,
                    height: sourceSize.height
                )
            )
        }
    }

    private func normalizedRotationDegrees(_ degrees: CGFloat) -> CGFloat {
        var normalized = degrees.truncatingRemainder(dividingBy: 360)
        if normalized > 180 {
            normalized -= 360
        } else if normalized <= -180 {
            normalized += 360
        }
        return normalized
    }
}
