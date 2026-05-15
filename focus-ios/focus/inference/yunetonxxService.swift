import Foundation
import CoreGraphics
import CoreVideo
import onnxruntime_objc

final class YuNetONNXService: YuNetDetecting {
    private let env: ORTEnv
    private let session: ORTSession
    private let inputName: String
    private let outputNames: Set<String>
    private let preprocessor = ImagePreprocessor.shared

    private let modelInputWidth = 640
    private let modelInputHeight = 640

    init(
        modelFileName: String = "face_detection_yunet_2023mar",
        modelFileExtension: String = "onnx"
    ) throws {
        guard let path = Bundle.main.path(forResource: modelFileName, ofType: modelFileExtension) else {
            throw InferenceError.modelFileNotFound("\(modelFileName).\(modelFileExtension)")
        }

        do {
            self.env = try ORTEnv(loggingLevel: ORTLoggingLevel.warning)
            let options = try ORTSessionOptions()
            try options.setIntraOpNumThreads(2)
            self.session = try ORTSession(env: env, modelPath: path, sessionOptions: options)

            let inputNames = try session.inputNames()
            let allOutputNames = try session.outputNames()

            guard let firstInput = inputNames.first as? String else {
                throw InferenceError.invalidModelOutput("YuNet input name을 찾을 수 없습니다.")
            }

            self.inputName = firstInput
            self.outputNames = Set(allOutputNames.compactMap { $0 as? String })

            print("[YuNet] input name:", self.inputName)
            print("[YuNet] all output names:", Array(self.outputNames).sorted())
        } catch {
            throw InferenceError.sessionInitializationFailed(error.localizedDescription)
        }
    }

    func detectFaces(from pixelBuffer: CVPixelBuffer) throws -> [DetectedFace] {
        let sourceWidth = CVPixelBufferGetWidth(pixelBuffer)
        let sourceHeight = CVPixelBufferGetHeight(pixelBuffer)

        let resized = try preprocessor.cropRGB(
            from: pixelBuffer,
            rect: CGRect(x: 0, y: 0, width: sourceWidth, height: sourceHeight),
            outputSize: CGSize(width: modelInputWidth, height: modelInputHeight)
        )

        let floatNCHW = preprocessor.uint8RGBToFloatNCHW(resized) { pixel in
            pixel / 255.0
        }

        let shape: [NSNumber] = [
            1,
            3,
            NSNumber(value: modelInputHeight),
            NSNumber(value: modelInputWidth)
        ]

        let inputTensor: ORTValue
        do {
            inputTensor = try ORTValue(
                tensorData: NSMutableData(data: floatNCHW),
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
                outputNames: outputNames,
                runOptions: nil
            )
        } catch {
            throw InferenceError.inferenceFailed("YuNet run 실패: \(error.localizedDescription)")
        }

        let cls16 = try readOutput(named: "cls_16", from: outputs)
        let cls32 = try readOutput(named: "cls_32", from: outputs)

        let obj16 = try readOutput(named: "obj_16", from: outputs)
        let obj32 = try readOutput(named: "obj_32", from: outputs)

        let bbox16 = try readOutput(named: "bbox_16", from: outputs)
        let bbox32 = try readOutput(named: "bbox_32", from: outputs)

        let kps16 = try readOutput(named: "kps_16", from: outputs)
        let kps32 = try readOutput(named: "kps_32", from: outputs)

        var predictions: [YuNetRawPrediction] = []

        // 우선 stride 8은 끈다.
        // 작은 박스가 너무 많이 나오기 때문에 디버깅 단계에서는 16/32만 사용
        decodeStride(
            stride: 16,
            cls: cls16,
            obj: obj16,
            bbox: bbox16,
            kps: kps16,
            sourceWidth: sourceWidth,
            sourceHeight: sourceHeight,
            predictions: &predictions
        )

        decodeStride(
            stride: 32,
            cls: cls32,
            obj: obj32,
            bbox: bbox32,
            kps: kps32,
            sourceWidth: sourceWidth,
            sourceHeight: sourceHeight,
            predictions: &predictions
        )

        print("[YuNet] raw predictions:", predictions.count)

        // score 높은 후보만 남긴 뒤 NMS
        let topCandidates = predictions
            .sorted { $0.confidence > $1.confidence }
            .prefix(100)

        let nms = nonMaximumSuppression(
            Array(topCandidates),
            iouThreshold: 0.25
        )

        print("[YuNet] nms predictions:", nms.count)

        return nms.map {
            DetectedFace(
                bbox: $0.bbox,
                landmarks: $0.landmarks,
                confidence: $0.confidence
            )
        }
    }

    private func readOutput(named name: String, from outputs: [String: ORTValue]) throws -> [Float] {
        guard let tensor = outputs[name] else {
            throw InferenceError.invalidModelOutput("YuNet output '\(name)' 없음")
        }

        do {
            let data = try tensor.tensorData() as Data
            return data.toFloatArray()
        } catch {
            throw InferenceError.invalidModelOutput("YuNet output '\(name)' tensorData 변환 실패: \(error.localizedDescription)")
        }
    }

    private func decodeStride(
        stride: Int,
        cls: [Float],
        obj: [Float],
        bbox: [Float],
        kps: [Float],
        sourceWidth: Int,
        sourceHeight: Int,
        predictions: inout [YuNetRawPrediction]
    ) {
        let featureWidth = modelInputWidth / stride
        let featureHeight = modelInputHeight / stride
        let count = featureWidth * featureHeight

        guard cls.count >= count,
              obj.count >= count,
              bbox.count >= count * 4,
              kps.count >= count * 10 else {
            print("[YuNet] stride \(stride) output size mismatch")
            return
        }

        let xScale = CGFloat(sourceWidth) / CGFloat(modelInputWidth)
        let yScale = CGFloat(sourceHeight) / CGFloat(modelInputHeight)

        for i in 0..<count {
            let clsScore = sigmoid(cls[i])
            let objScore = sigmoid(obj[i])
            let score = clsScore * objScore

            if score < 0.42 {
                continue
            }

            let gy = i / featureWidth
            let gx = i % featureWidth

            let centerX = CGFloat(gx) * CGFloat(stride)
            let centerY = CGFloat(gy) * CGFloat(stride)

            let b = i * 4
            let l = CGFloat(bbox[b + 0]) * CGFloat(stride)
            let t = CGFloat(bbox[b + 1]) * CGFloat(stride)
            let r = CGFloat(bbox[b + 2]) * CGFloat(stride)
            let bb = CGFloat(bbox[b + 3]) * CGFloat(stride)

            let x1 = centerX - l
            let y1 = centerY - t
            let x2 = centerX + r
            let y2 = centerY + bb

            let rect = CGRect(
                x: x1 * xScale,
                y: y1 * yScale,
                width: (x2 - x1) * xScale,
                height: (y2 - y1) * yScale
            ).intersection(CGRect(x: 0, y: 0, width: sourceWidth, height: sourceHeight))

            if rect.isNull || rect.isEmpty { continue }

            // 너무 작은 후보 제거
            if rect.width < 60 || rect.height < 60 { continue }

            let aspect = rect.width / rect.height
            if aspect < 0.6 || aspect > 1.6 { continue }

            let area = rect.width * rect.height
            if area < 5000 { continue }

            let kpBase = i * 10
            let points: [CGPoint] = (0..<5).map { j in
                let dx = CGFloat(kps[kpBase + j * 2 + 0]) * CGFloat(stride)
                let dy = CGFloat(kps[kpBase + j * 2 + 1]) * CGFloat(stride)

                return CGPoint(
                    x: (centerX + dx) * xScale,
                    y: (centerY + dy) * yScale
                )
            }

            let landmarks = FaceLandmarks5(
                leftEye: points[0],
                rightEye: points[1],
                nose: points[2],
                leftMouth: points[3],
                rightMouth: points[4]
            )

            predictions.append(
                YuNetRawPrediction(
                    bbox: rect,
                    landmarks: landmarks,
                    confidence: score
                )
            )
        }
    }

    private func sigmoid(_ x: Float) -> Float {
        1.0 / (1.0 + exp(-x))
    }

    private func nonMaximumSuppression(
        _ boxes: [YuNetRawPrediction],
        iouThreshold: CGFloat
    ) -> [YuNetRawPrediction] {
        let sorted = boxes.sorted { $0.confidence > $1.confidence }
        var keep: [YuNetRawPrediction] = []

        for candidate in sorted {
            let shouldKeep = keep.allSatisfy { existing in
                iou(candidate.bbox, existing.bbox) < iouThreshold
            }
            if shouldKeep {
                keep.append(candidate)
            }
        }

        return keep
    }

    private func iou(_ a: CGRect, _ b: CGRect) -> CGFloat {
        let inter = a.intersection(b)
        if inter.isNull || inter.isEmpty { return 0 }

        let interArea = inter.width * inter.height
        let unionArea = a.width * a.height + b.width * b.height - interArea
        guard unionArea > 0 else { return 0 }

        return interArea / unionArea
    }
}

private extension Data {
    func toFloatArray() -> [Float] {
        let count = self.count / MemoryLayout<Float>.stride
        return self.withUnsafeBytes { raw in
            let ptr = raw.bindMemory(to: Float.self)
            return Array(ptr.prefix(count))
        }
    }
}
