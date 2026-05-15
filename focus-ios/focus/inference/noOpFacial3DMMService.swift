//
//  noOpFacial3DMMService.swift
//  focus
//
//  Created by Codex on 4/7/26.
//

import Foundation
import CoreVideo

final class NoOpFacial3DMMService: Facial3DMMInferring {
    func inferTDMM(from pixelBuffer: CVPixelBuffer, face: DetectedFace) throws -> TDMMCoefficients? {
        _ = pixelBuffer
        _ = face
        return nil
    }
}
