//
//  facial3DMMTFLiteService.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation
import CoreGraphics
import CoreVideo
#if canImport(TensorFlowLite)
import TensorFlowLite
#endif

#if canImport(TensorFlowLite)
final class Facial3DMMTFLiteService: Facial3DMMInferring {
    private let interpreter: Interpreter
    private let preprocessor = ImagePreprocessor.shared

    init(
        modelFileName: String = "facemap_3dmm-facial-landmark-detection-float",
        modelFileExtension: String = "tflite",
        threadCount: Int = 2
    ) throws {
        guard let path = Bundle.main.path(forResource: modelFileName, ofType: modelFileExtension) else {
            throw InferenceError.modelFileNotFound("\(modelFileName).\(modelFileExtension)")
        }

        do {
            var options = Interpreter.Options()
            options.threadCount = threadCount
            self.interpreter = try Interpreter(modelPath: path, options: options)
            try self.interpreter.allocateTensors()
        } catch {
            throw InferenceError.sessionInitializationFailed("TFLite interpreter 초기화 실패: \(error.localizedDescription)")
        }
    }

    func inferTDMM(from pixelBuffer: CVPixelBuffer, face: DetectedFace) throws -> TDMMCoefficients? {
        let crop = face.bbox
        if crop.width < FocusConstants.minEmbeddingCropSize || crop.height < FocusConstants.minEmbeddingCropSize {
            return nil
        }

        let image = try preprocessor.cropRGB(
            from: pixelBuffer,
            rect: crop,
            outputSize: FocusConstants.tdmmInputSize
        )

        let inputTensor = try interpreter.input(at: 0)

        switch inputTensor.dataType {
        case .uInt8:
            try interpreter.copy(image.data, toInputAt: 0)

        case .float32:
            let floatData = preprocessor.uint8RGBToFloatNHWC(image, scale: 1.0 / 255.0)
            try interpreter.copy(floatData, toInputAt: 0)

        default:
            throw InferenceError.unsupportedTensorType("3DMM input dtype: \(inputTensor.dataType)")
        }

        do {
            try interpreter.invoke()
        } catch {
            throw InferenceError.inferenceFailed("3DMM invoke 실패: \(error.localizedDescription)")
        }

        let outputTensor = try interpreter.output(at: 0)
        let coeffs = try decodeOutput(outputTensor)

        guard coeffs.count == 265 else {
            throw InferenceError.invalidTDMMLayout(coeffs.count)
        }

        let id = Array(coeffs[0..<219])
        let exp = Array(coeffs[219..<258])
        let pose = Array(coeffs[258..<264])
        let extra = Array(coeffs[264..<265])

        return TDMMCoefficients(
            id: id,
            exp: exp,
            pose: pose,
            extra: extra
        )
    }

    private func decodeOutput(_ tensor: Tensor) throws -> [Float] {
        switch tensor.dataType {
        case .float32:
            return tensor.data.toFloatArray()

        case .uInt8:
            guard let params = tensor.quantizationParameters else {
                throw InferenceError.invalidModelOutput("uInt8 output인데 quantizationParameters가 없습니다.")
            }
            let values = [UInt8](tensor.data)
            return values.map { (Float($0) - Float(params.zeroPoint)) * params.scale }

        default:
            throw InferenceError.unsupportedTensorType("3DMM output dtype: \(tensor.dataType)")
        }
    }
}
#else
final class Facial3DMMTFLiteService: Facial3DMMInferring {
    init(
        modelFileName: String = "facemap_3dmm-facial-landmark-detection-float",
        modelFileExtension: String = "tflite",
        threadCount: Int = 2
    ) throws {
        _ = modelFileName
        _ = modelFileExtension
        _ = threadCount

        throw InferenceError.sessionInitializationFailed(
            "TensorFlowLite 모듈을 찾을 수 없습니다. Pod 설치 또는 framework 연결 상태를 확인해 주세요."
        )
    }

    func inferTDMM(from pixelBuffer: CVPixelBuffer, face: DetectedFace) throws -> TDMMCoefficients? {
        _ = pixelBuffer
        _ = face
        throw InferenceError.sessionInitializationFailed(
            "3DMM 런타임이 연결되지 않았습니다."
        )
    }
}
#endif

// MARK: - Data Helpers
private extension Data {
    func toFloatArray() -> [Float] {
        let count = self.count / MemoryLayout<Float>.stride
        return self.withUnsafeBytes { raw in
            let ptr = raw.bindMemory(to: Float.self)
            return Array(ptr)
        }
    }
}

private extension Array where Element == UInt8 {
    init(_ data: Data) {
        self = data.withUnsafeBytes { raw in
            Array(raw.bindMemory(to: UInt8.self))
        }
    }
}
