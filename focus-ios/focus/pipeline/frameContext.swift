//
//  frameContext.swift
//  focus
//
//  Created by 이동언 on 3/7/26.
//

import Foundation
import AVFoundation
import CoreVideo
import CoreGraphics

enum PipelineInputMode {
    case preview
    case recording
}

struct FrameContext {
    let pixelBuffer: CVPixelBuffer
    let videoSampleBuffer: CMSampleBuffer?
    let pts: CMTime
    let ptsUs: Int64
    let sessionID: String?
    let frameIndex: Int
    let mode: PipelineInputMode
}
