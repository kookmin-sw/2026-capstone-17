//
//  YuNetOpenCVService.swift
//  focus
//
//  Created by 이동언 on 3/9/26.
//


import Foundation
import CoreGraphics
import CoreVideo

final class YuNetOpenCVService: YuNetDetecting {
    private let wrapper: OpenCVYuNetWrapper

    init(
        modelFileName: String = "face_detection_yunet_2023mar",
        modelFileExtension: String = "onnx",
        inputSize: Int = 320,
        scoreThreshold: Float = 0.5,
        nmsThreshold: Float = 0.3,
        topK: Int = 5000
    ) throws {
        guard let modelPath = Bundle.main.path(forResource: modelFileName, ofType: modelFileExtension) else {
            throw InferenceError.modelFileNotFound("\(modelFileName).\(modelFileExtension)")
        }

        guard let wrapper = OpenCVYuNetWrapper(
            modelPath: modelPath,
            inputSize: Int32(inputSize),
            scoreThreshold: scoreThreshold,
            nmsThreshold: nmsThreshold,
            topK: Int32(topK)
        ) else {
            throw InferenceError.sessionInitializationFailed("OpenCV YuNet wrapper 초기화 실패")
        }

        self.wrapper = wrapper
    }

    func detectFaces(from pixelBuffer: CVPixelBuffer) throws -> [DetectedFace] {
        let raw = wrapper.detectFaces(in: pixelBuffer)

        return raw.compactMap { item in
            guard
                let x = item["x"] as? NSNumber,
                let y = item["y"] as? NSNumber,
                let width = item["width"] as? NSNumber,
                let height = item["height"] as? NSNumber,
                let score = item["score"] as? NSNumber,
                let leftEyeX = item["leftEyeX"] as? NSNumber,
                let leftEyeY = item["leftEyeY"] as? NSNumber,
                let rightEyeX = item["rightEyeX"] as? NSNumber,
                let rightEyeY = item["rightEyeY"] as? NSNumber,
                let noseX = item["noseX"] as? NSNumber,
                let noseY = item["noseY"] as? NSNumber,
                let leftMouthX = item["leftMouthX"] as? NSNumber,
                let leftMouthY = item["leftMouthY"] as? NSNumber,
                let rightMouthX = item["rightMouthX"] as? NSNumber,
                let rightMouthY = item["rightMouthY"] as? NSNumber
            else {
                return nil
            }

            let bbox = CGRect(
                x: x.doubleValue,
                y: y.doubleValue,
                width: width.doubleValue,
                height: height.doubleValue
            )

            let landmarks = FaceLandmarks5(
                leftEye: CGPoint(x: leftEyeX.doubleValue, y: leftEyeY.doubleValue),
                rightEye: CGPoint(x: rightEyeX.doubleValue, y: rightEyeY.doubleValue),
                nose: CGPoint(x: noseX.doubleValue, y: noseY.doubleValue),
                leftMouth: CGPoint(x: leftMouthX.doubleValue, y: leftMouthY.doubleValue),
                rightMouth: CGPoint(x: rightMouthX.doubleValue, y: rightMouthY.doubleValue)
            )

            return DetectedFace(
                bbox: bbox,
                landmarks: landmarks,
                confidence: score.floatValue
            )
        }
    }
}
