//
//  faceDetectionTypes.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation
import CoreGraphics

struct FaceLandmarks5: Equatable, Sendable {
    let leftEye: CGPoint
    let rightEye: CGPoint
    let nose: CGPoint
    let leftMouth: CGPoint
    let rightMouth: CGPoint

    var allPoints: [CGPoint] {
        [leftEye, rightEye, nose, leftMouth, rightMouth]
    }
}

struct DetectedFace: Equatable, Sendable {
    let bbox: CGRect
    let landmarks: FaceLandmarks5?
    let confidence: Float

    var hasLandmarks: Bool {
        landmarks != nil
    }

    var minSide: CGFloat {
        min(bbox.width, bbox.height)
    }
}
