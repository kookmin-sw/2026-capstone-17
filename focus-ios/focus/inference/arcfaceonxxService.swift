//
//  arcfaceonxxService.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation
import CoreGraphics
import CoreVideo
#if canImport(onnxruntime_objc)
import onnxruntime_objc
#endif

#if canImport(onnxruntime_objc)
final class ArcFaceONNXService: ArcFaceEmbeddingExtracting {
    private let env: ORTEnv
    private let session: ORTSession
    private let inputName: String
    private let outputName: String
    private let preprocessor = ImagePreprocessor.shared

    init(
        modelFileName: String = "w600k_mbf",
        modelFileExtension: String = "onnx",
        inputName: String = "input",
        outputName: String = "output"
    ) throws {
        guard let path = Bundle.main.path(forResource: modelFileName, ofType: modelFileExtension) else {
            throw InferenceError.modelFileNotFound("\(modelFileName).\(modelFileExtension)")
        }

        do {
            self.env = try ORTEnv(loggingLevel: ORTLoggingLevel.warning)
            let options = try ORTSessionOptions()
            try options.setIntraOpNumThreads(2)
            self.session = try ORTSession(env: env, modelPath: path, sessionOptions: options)

            let availableInputNames = try session.inputNames().compactMap { $0 as? String }
            let availableOutputNames = try session.outputNames().compactMap { $0 as? String }

            guard let resolvedInputName = ArcFaceModelIOResolver.resolveInputName(
                preferredName: inputName,
                availableNames: availableInputNames
            ) else {
                throw InferenceError.invalidModelOutput("ArcFace input name을 찾을 수 없습니다.")
            }

            guard let resolvedOutputName = ArcFaceModelIOResolver.resolveOutputName(
                preferredName: outputName,
                availableNames: availableOutputNames
            ) else {
                throw InferenceError.invalidModelOutput("ArcFace output name을 찾을 수 없습니다.")
            }

            self.inputName = resolvedInputName
            self.outputName = resolvedOutputName
        } catch {
            throw InferenceError.sessionInitializationFailed(error.localizedDescription)
        }
    }

    func extractEmbedding(from pixelBuffer: CVPixelBuffer, face: DetectedFace) throws -> [Float] {
        let bbox = face.bbox
        guard bbox.width >= FocusConstants.minEmbeddingCropSize,
              bbox.height >= FocusConstants.minEmbeddingCropSize else {
            throw InferenceError.cropTooSmall
        }

        let image = try preprocessor.cropAlignedRGBForRecognition(
            from: pixelBuffer,
            rect: bbox,
            landmarks: face.landmarks,
            outputSize: FocusConstants.arcFaceInputSize
        )

        let nchw = preprocessor.uint8RGBToFloatNCHW(image) { pixel in
            (pixel - 127.5) / 128.0
        }

        let shape: [NSNumber] = [1, 3, NSNumber(value: image.height), NSNumber(value: image.width)]

        let inputTensor: ORTValue
        do {
            inputTensor = try ORTValue(
                tensorData: NSMutableData(data: nchw),
                elementType: ORTTensorElementDataType.float,
                shape: shape
            )
        } catch {
            throw InferenceError.tensorCreationFailed(error.localizedDescription)
        }

        let outputs: [String: ORTValue]
        do {
            outputs = try session.run(
                withInputs: [inputName: inputTensor],
                outputNames: Set([outputName]),
                runOptions: nil
            )
        } catch {
            throw InferenceError.inferenceFailed("ArcFace run 실패: \(error.localizedDescription)")
        }

        guard let outputTensor = outputs[outputName] else {
            throw InferenceError.invalidModelOutput("ArcFace output '\(outputName)' 없음")
        }

        let outputData: Data
        do {
            outputData = try outputTensor.tensorData() as Data
        } catch {
            throw InferenceError.invalidModelOutput("ArcFace output tensorData 변환 실패: \(error.localizedDescription)")
        }

        let embedding = outputData.toFloatArray()
        guard !embedding.isEmpty else {
            throw InferenceError.emptyEmbedding
        }

        return preprocessor.l2Normalize(embedding)
    }
}
#else
final class ArcFaceONNXService: ArcFaceEmbeddingExtracting {
    init(
        modelFileName: String = "w600k_mbf",
        modelFileExtension: String = "onnx",
        inputName: String = "input",
        outputName: String = "output"
    ) throws {
        _ = modelFileName
        _ = modelFileExtension
        _ = inputName
        _ = outputName

        throw InferenceError.sessionInitializationFailed(
            "onnxruntime_objc 모듈을 찾을 수 없습니다. Pod 설치 또는 framework 연결 상태를 확인해 주세요."
        )
    }

    func extractEmbedding(from pixelBuffer: CVPixelBuffer, face: DetectedFace) throws -> [Float] {
        _ = pixelBuffer
        _ = face
        throw InferenceError.sessionInitializationFailed(
            "ArcFace 런타임이 연결되지 않았습니다."
        )
    }
}
#endif

struct ArcFaceModelIOResolver {
    static func resolveInputName(preferredName: String, availableNames: [String]) -> String? {
        guard !availableNames.isEmpty else { return nil }

        if availableNames.contains(preferredName) {
            return preferredName
        }

        if let canonical = availableNames.first(where: { normalized($0) == normalized(preferredName) }) {
            return canonical
        }

        return availableNames.first
    }

    static func resolveOutputName(preferredName: String, availableNames: [String]) -> String? {
        guard !availableNames.isEmpty else { return nil }

        if availableNames.contains(preferredName) {
            return preferredName
        }

        if let canonical = availableNames.first(where: { normalized($0) == normalized(preferredName) }) {
            return canonical
        }

        let priorityTokens = [
            "fc1",
            "embedding",
            "emb",
            "output"
        ]

        for token in priorityTokens {
            if let matched = availableNames.first(where: { normalized($0).contains(token) }) {
                return matched
            }
        }

        return availableNames.first
    }

    private static func normalized(_ value: String) -> String {
        value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
    }
}

// MARK: - Data Helpers
private extension Data {
    func toFloatArray() -> [Float] {
        let count = self.count / MemoryLayout<Float>.stride
        return self.withUnsafeBytes { raw in
            let ptr = raw.bindMemory(to: Float.self)
            return Array(ptr.prefix(count))
        }
    }
}
