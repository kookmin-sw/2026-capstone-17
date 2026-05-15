//
//  previewRenderer.swift
//  focus
//
//  Created by 이동언 on 3/7/26.
//

import Foundation
import CoreVideo
import CoreImage
import CoreGraphics

final class PreviewRenderer {
    private let ciContext = CIContext(options: nil)
    private let privacyMaskRenderer: PrivacyMaskRenderer
    private let targetPool: RenderTargetPool

    init(
        privacyMaskRenderer: PrivacyMaskRenderer = PrivacyMaskRenderer(),
        targetPool: RenderTargetPool = RenderTargetPool()
    ) {
        self.privacyMaskRenderer = privacyMaskRenderer
        self.targetPool = targetPool
    }

    func renderPreviewFrame(
        source pixelBuffer: CVPixelBuffer,
        tracks: [TrackedFace]
    ) -> CVPixelBuffer? {
        let width = CVPixelBufferGetWidth(pixelBuffer)
        let height = CVPixelBufferGetHeight(pixelBuffer)

        guard let target = targetPool.makePixelBuffer(width: width, height: height) else {
            return nil
        }

        let baseImage = CIImage(cvPixelBuffer: pixelBuffer)
        let outputImage = privacyMaskRenderer.applyMasks(to: baseImage, tracks: tracks)

        ciContext.render(outputImage, to: target)
        return target
    }
}
