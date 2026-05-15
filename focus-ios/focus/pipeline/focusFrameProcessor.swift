//
//  focusFrameProcessor.swift
//  focus
//
//  Created by Codex on 4/11/26.
//

import Foundation
import CoreVideo

struct FocusProcessedFrame {
    let detections: [DetectedFace]
    let tdmmList: [TDMMCoefficients?]
    let trackedFaces: [TrackedFace]
    let trackingIDs: [Int]
    let frameWidth: Int
    let frameHeight: Int
    let timestampMs: Int64
}

final class FocusFrameProcessor {
    let tracker: FaceTracker
    let stateMachine: TrackStateMachine
    let arcFaceExtractor: ArcFaceEmbeddingExtracting?

    private let detector: YuNetDetecting
    private let tdmmInferencer: Facial3DMMInferring
    private let lock = NSLock()

    init(
        detector: YuNetDetecting,
        tdmmInferencer: Facial3DMMInferring,
        tracker: FaceTracker,
        stateMachine: TrackStateMachine,
        arcFaceExtractor: ArcFaceEmbeddingExtracting?
    ) {
        self.detector = detector
        self.tdmmInferencer = tdmmInferencer
        self.tracker = tracker
        self.stateMachine = stateMachine
        self.arcFaceExtractor = arcFaceExtractor
    }

    func process(
        pixelBuffer: CVPixelBuffer,
        timestampMs: Int64,
        frameIndex: Int?
    ) throws -> FocusProcessedFrame {
        lock.lock()
        defer { lock.unlock() }

        let rawDetections = try detector.detectFaces(from: pixelBuffer)
        let dedupedDetections = DuplicateFaceFilter.dedupeDetections(rawDetections)
        let detections = dedupedDetections
            .filter { $0.confidence >= FocusConstants.yunetConfidenceThreshold }

        let tdmmList: [TDMMCoefficients?]
        if detections.isEmpty {
            tdmmList = []
        } else {
            tdmmList = try detections.map { detection in
                try tdmmInferencer.inferTDMM(from: pixelBuffer, face: detection)
            }
        }

        let trackingIDs: [Int]
        if let frameIndex, !detections.isEmpty {
            trackingIDs = tracker.updateTrackingIDs(
                detections: detections,
                tdmmList: tdmmList,
                frameIndex: frameIndex
            )
        } else {
            trackingIDs = Array(detections.indices)
        }

        if !detections.isEmpty {
            stateMachine.beginFrame(seenTrackIDs: Set(trackingIDs))

            for detectionIndex in detections.indices {
                guard tdmmList.indices.contains(detectionIndex),
                      tdmmList[detectionIndex] != nil else {
                    continue
                }

                let trackID = trackingIDs[detectionIndex]
                stateMachine.recordFrameSeen(trackID: trackID)

                guard let arcFaceExtractor else { continue }

                let isFrontal = FocusFrameProcessor.isFrontalFace(
                    landmarks: detections[detectionIndex].landmarks
                )

                guard stateMachine.needsEmbeddingThisFrame(
                    trackID: trackID,
                    isFrontal: isFrontal
                ) else {
                    continue
                }

                let bbox = detections[detectionIndex].bbox
                guard bbox.width >= FocusConstants.minEmbeddingCropSize,
                      bbox.height >= FocusConstants.minEmbeddingCropSize else {
                    continue
                }

                let embedding = try arcFaceExtractor.extractEmbedding(
                    from: pixelBuffer,
                    face: detections[detectionIndex]
                )

                switch stateMachine.label(for: trackID) {
                case nil, .some(.pending):
                    stateMachine.addEmbedding(trackID: trackID, embedding: embedding)
                case .other:
                    _ = stateMachine.recheckFrontal(trackID: trackID, embedding: embedding)
                case .owner:
                    break
                }
            }
        } else if let frameIndex {
            _ = tracker.updateTrackingIDs(
                detections: [],
                tdmmList: [],
                frameIndex: frameIndex
            )
        }

        var trackedFaces: [TrackedFace] = []
        trackedFaces.reserveCapacity(detections.count)

        for detectionIndex in detections.indices {
            let trackID = trackingIDs[detectionIndex]
            var track = tracker.track(withID: trackID) ?? TrackedFace(
                trackID: trackID,
                bbox: detections[detectionIndex].bbox,
                landmarks: detections[detectionIndex].landmarks,
                tdmm: tdmmList[detectionIndex],
                label: .pending,
                ownerID: nil,
                age: 1,
                missedFrames: 0,
                frontalEmbeddingSamples: [],
                hasRetriedOther: false,
                framesSeen: 0,
                lastSeenFrameIndex: frameIndex ?? 0
            )

            track.bbox = detections[detectionIndex].bbox
            track.landmarks = detections[detectionIndex].landmarks
            track.tdmm = tdmmList[detectionIndex]
            track.missedFrames = 0
            if let frameIndex {
                track.lastSeenFrameIndex = frameIndex
            }
            stateMachine.applyState(to: &track)
            trackedFaces.append(track)
        }

        tracker.mergeAnnotations(from: trackedFaces)

        return FocusProcessedFrame(
            detections: detections,
            tdmmList: tdmmList,
            trackedFaces: trackedFaces,
            trackingIDs: trackingIDs,
            frameWidth: CVPixelBufferGetWidth(pixelBuffer),
            frameHeight: CVPixelBufferGetHeight(pixelBuffer),
            timestampMs: timestampMs
        )
    }

    func reset() {
        lock.lock()
        tracker.reset()
        stateMachine.clear()
        lock.unlock()
    }

    static func isFrontalFace(landmarks: FaceLandmarks5?) -> Bool {
        guard let lm = landmarks else { return false }

        let eyeCenterX = (lm.leftEye.x + lm.rightEye.x) / 2.0
        let dx = lm.leftEye.x - lm.rightEye.x
        let dy = lm.leftEye.y - lm.rightEye.y
        let eyeDistance = sqrt(dx * dx + dy * dy)

        guard eyeDistance > 0 else { return false }

        let noseOffset = abs(lm.nose.x - eyeCenterX) / eyeDistance
        return noseOffset < FocusConstants.frontalThreshold
    }
}
