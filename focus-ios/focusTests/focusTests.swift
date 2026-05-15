//
//  focusTests.swift
//  focusTests
//
//  Created by 이동언 on 3/7/26.
//

import Testing
import CoreGraphics
@testable import focus

struct focusTests {
    @Test func pendingTrackBecomesOwnerAfterEnoughSamples() async throws {
        let ownerStore = OwnerEmbeddingStore()
        _ = ownerStore.addOwner(embedding: [1, 0, 0])

        let stateMachine = TrackStateMachine(
            ownerStore: ownerStore,
            classifier: OwnerOtherClassifier(similarityThreshold: 0.4)
        )

        var track = TrackedFace(
            trackID: 1,
            bbox: CGRect(x: 0, y: 0, width: 120, height: 120),
            landmarks: nil,
            tdmm: nil,
            label: .pending,
            age: 1,
            missedFrames: 0,
            frontalEmbeddingSamples: [],
            hasRetriedOther: false,
            framesSeen: 6,
            lastSeenFrameIndex: 6
        )

        stateMachine.updateLabel(track: &track, newEmbedding: [1, 0, 0], isFrontal: true)
        stateMachine.updateLabel(track: &track, newEmbedding: [0.95, 0.05, 0], isFrontal: true)
        #expect(track.label == .pending)

        stateMachine.updateLabel(track: &track, newEmbedding: [0.9, 0.1, 0], isFrontal: true)
        #expect(track.label == .owner)
    }

    @Test func metadataPolicyDropsOwnerButKeepsOtherWithValidTDMM() async throws {
        let validTDMM = TDMMCoefficients(
            id: Array(repeating: 0.1, count: TDMMCoefficients.idCount),
            exp: Array(repeating: 0.2, count: TDMMCoefficients.expCount),
            pose: Array(repeating: 0.3, count: TDMMCoefficients.poseCount),
            extra: [0.4]
        )

        let ownerTrack = TrackedFace(
            trackID: 7,
            bbox: CGRect(x: 10, y: 20, width: 30, height: 40),
            landmarks: nil,
            tdmm: validTDMM,
            label: .owner,
            age: 1,
            missedFrames: 0,
            frontalEmbeddingSamples: [],
            hasRetriedOther: false,
            framesSeen: 10,
            lastSeenFrameIndex: 10
        )

        let otherTrack = TrackedFace(
            trackID: 8,
            bbox: CGRect(x: 10, y: 20, width: 30, height: 40),
            landmarks: nil,
            tdmm: validTDMM,
            label: .other,
            age: 1,
            missedFrames: 0,
            frontalEmbeddingSamples: [],
            hasRetriedOther: false,
            framesSeen: 10,
            lastSeenFrameIndex: 10
        )

        let ownerResult = MetadataPolicy.evaluate(track: ownerTrack)
        let otherResult = MetadataPolicy.evaluate(track: otherTrack)

        #expect(ownerResult.shouldInclude == false)
        #expect(ownerResult.reason == .ownerNotAllowed)
        #expect(otherResult.shouldInclude == true)
        #expect(otherResult.reason == nil)
    }

    @Test func trackCostFallsBackToIoUWhenTDMMIsMissing() async throws {
        let detection = DetectedFace(
            bbox: CGRect(x: 0, y: 0, width: 100, height: 100),
            landmarks: nil,
            confidence: 0.9
        )

        let track = TrackedFace(
            trackID: 5,
            bbox: CGRect(x: 5, y: 5, width: 100, height: 100),
            landmarks: nil,
            tdmm: nil,
            label: .pending,
            age: 1,
            missedFrames: 0,
            frontalEmbeddingSamples: [],
            hasRetriedOther: false,
            framesSeen: 3,
            lastSeenFrameIndex: 3
        )

        let candidate = TrackCost.combinedCost(
            track: track,
            detection: detection,
            detectionTDMM: nil
        )

        #expect(candidate != nil)
    }

    @Test func previewHitTesterSelectsTrackContainingTap() async throws {
        let hitTester = PreviewTrackHitTester()
        let tracks = [
            TrackedFace(
                trackID: 1,
                bbox: CGRect(x: 100, y: 100, width: 120, height: 120),
                landmarks: nil,
                tdmm: nil,
                label: .pending,
                age: 1,
                missedFrames: 0,
                frontalEmbeddingSamples: [],
                hasRetriedOther: false,
                framesSeen: 1,
                lastSeenFrameIndex: 1
            ),
            TrackedFace(
                trackID: 2,
                bbox: CGRect(x: 320, y: 100, width: 120, height: 120),
                landmarks: nil,
                tdmm: nil,
                label: .pending,
                age: 1,
                missedFrames: 0,
                frontalEmbeddingSamples: [],
                hasRetriedOther: false,
                framesSeen: 1,
                lastSeenFrameIndex: 1
            )
        ]

        let selectedTrackID = hitTester.nearestTrackID(
            to: CGPoint(x: 150, y: 150),
            previewSize: CGSize(width: 640, height: 360),
            tracks: tracks,
            sourceSize: CGSize(width: 640, height: 360),
            isMirrored: false,
            rotationDegrees: 0
        )

        #expect(selectedTrackID == 1)
    }

    @Test func previewHitTesterReturnsNilForDistantTap() async throws {
        let hitTester = PreviewTrackHitTester()
        let tracks = [
            TrackedFace(
                trackID: 3,
                bbox: CGRect(x: 40, y: 40, width: 80, height: 80),
                landmarks: nil,
                tdmm: nil,
                label: .pending,
                age: 1,
                missedFrames: 0,
                frontalEmbeddingSamples: [],
                hasRetriedOther: false,
                framesSeen: 1,
                lastSeenFrameIndex: 1
            )
        ]

        let selectedTrackID = hitTester.nearestTrackID(
            to: CGPoint(x: 620, y: 330),
            previewSize: CGSize(width: 640, height: 360),
            tracks: tracks,
            sourceSize: CGSize(width: 640, height: 360),
            isMirrored: false,
            rotationDegrees: 0
        )

        #expect(selectedTrackID == nil)
    }

    @Test func arcFaceResolverFallsBackToModelInputName() async throws {
        let resolved = ArcFaceModelIOResolver.resolveInputName(
            preferredName: "input",
            availableNames: ["input.1"]
        )

        #expect(resolved == "input.1")
    }

    @Test func arcFaceResolverPrefersSemanticOutputNameBeforeFallback() async throws {
        let resolved = ArcFaceModelIOResolver.resolveOutputName(
            preferredName: "output",
            availableNames: ["685", "fc1"]
        )

        #expect(resolved == "fc1")
    }
}
