//
//  inferenceTypes.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation
import CoreGraphics
import CoreVideo

enum InferenceError: LocalizedError {
    case modelFileNotFound(String)
    case sessionInitializationFailed(String)
    case preprocessingFailed(String)
    case invalidModelOutput(String)
    case outputShapeMismatch(String)
    case tensorCreationFailed(String)
    case inferenceFailed(String)
    case unsupportedTensorType(String)
    case cropTooSmall
    case emptyEmbedding
    case invalidTDMMLayout(Int)

    var errorDescription: String? {
        switch self {
        case .modelFileNotFound(let name):
            return "모델 파일을 찾을 수 없습니다: \(name)"
        case .sessionInitializationFailed(let message):
            return "세션 초기화 실패: \(message)"
        case .preprocessingFailed(let message):
            return "전처리 실패: \(message)"
        case .invalidModelOutput(let message):
            return "모델 출력이 올바르지 않습니다: \(message)"
        case .outputShapeMismatch(let message):
            return "출력 shape 불일치: \(message)"
        case .tensorCreationFailed(let message):
            return "텐서 생성 실패: \(message)"
        case .inferenceFailed(let message):
            return "추론 실패: \(message)"
        case .unsupportedTensorType(let message):
            return "지원하지 않는 tensor type: \(message)"
        case .cropTooSmall:
            return "얼굴 crop 크기가 너무 작습니다."
        case .emptyEmbedding:
            return "임베딩 결과가 비어 있습니다."
        case .invalidTDMMLayout(let count):
            return "3DMM coeff 길이가 265가 아닙니다. 현재 길이: \(count)"
        }
    }
}

struct ImageTensorData {
    let data: Data
    let width: Int
    let height: Int
    let channels: Int
}

struct ResizeMeta {
    let originalWidth: Int
    let originalHeight: Int
    let resizedWidth: Int
    let resizedHeight: Int
    let scale: CGFloat
}

struct YuNetRawPrediction {
    let bbox: CGRect
    let landmarks: FaceLandmarks5?
    let confidence: Float
}

protocol YuNetDetecting {
    func detectFaces(from pixelBuffer: CVPixelBuffer) throws -> [DetectedFace]
}

protocol Facial3DMMInferring {
    func inferTDMM(from pixelBuffer: CVPixelBuffer, face: DetectedFace) throws -> TDMMCoefficients?
}

protocol ArcFaceEmbeddingExtracting {
    func extractEmbedding(from pixelBuffer: CVPixelBuffer, face: DetectedFace) throws -> [Float]
}
