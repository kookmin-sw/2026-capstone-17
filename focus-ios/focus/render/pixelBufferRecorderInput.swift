//
//  pixelBufferRecorderInput.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation
import AVFoundation
import CoreVideo

final class PixelBufferRecorderInput {
    private let adaptor: AVAssetWriterInputPixelBufferAdaptor
    private let writerInput: AVAssetWriterInput

    init(
        adaptor: AVAssetWriterInputPixelBufferAdaptor,
        writerInput: AVAssetWriterInput
    ) {
        self.adaptor = adaptor
        self.writerInput = writerInput
    }

    func append(pixelBuffer: CVPixelBuffer, withPresentationTime pts: CMTime) -> Bool {
        guard writerInput.isReadyForMoreMediaData else {
            return false
        }

        return adaptor.append(pixelBuffer, withPresentationTime: pts)
    }

    func markAsFinished() {
        writerInput.markAsFinished()
    }
}
