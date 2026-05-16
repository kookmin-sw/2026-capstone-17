//
//  focusPipelineController.swift
//  focus
//
//  Created by 이동언 on 3/7/26.
//

import Foundation
import AVFoundation
import CoreVideo

final class FocusPipelineController {
    private struct ManualOwnerBinding {
        let ownerID: UUID
        var lastUpgradeAt: Date?
    }

    private struct SoleOwnerVisibilityLock {
        let ownerID: UUID
        var lockedTrackID: Int?
        var lastLockedTrack: TrackedFace?
        var confirmedFrames: Int
        var missingFrames: Int

        var isActive: Bool {
            confirmedFrames >= FocusConstants.soleOwnerLockConfirmFrames
        }
    }

    // MARK: - Public
    private(set) var state: PipelineState = .idle
    var shouldMaskRecordingFaces = true

    var onPreviewFrame: ((CVPixelBuffer, [TrackedFace], [DetectedFace], [TrackedFace]) -> Void)?
    var onDebugSnapshot: ((PipelineDebugSnapshot) -> Void)?
    var onStateChanged: ((PipelineState) -> Void)?
    var onSessionFinished: ((PipelineSessionOutputs) -> Void)?
    var onOwnerStoreChanged: (() -> Void)?
    var onLiveBroadcastFirstVideoFrame: (() -> Void)?

    // MARK: - Queues
    private let inferenceQueue = DispatchQueue(label: "focus.pipeline.inferenceQueue", qos: .userInitiated)
    private let labelRefineQueue = DispatchQueue(label: "focus.pipeline.labelRefineQueue", qos: .utility)
    private let ownerTaskQueue = DispatchQueue(label: "focus.pipeline.ownerTaskQueue", qos: .utility)
    private let encoderQueue = DispatchQueue(label: "focus.pipeline.encoderQueue", qos: .userInitiated)

    private let asyncGroup = DispatchGroup()
    private let lock = NSLock()

    // MARK: - Dependencies
    private let frameProcessor: FocusFrameProcessor
    private let detector: YuNetDetecting
    private let tdmmInferencer: Facial3DMMInferring
    private let arcFaceExtractor: ArcFaceEmbeddingExtracting?
    private let tracker: FaceTracker
    private let stateMachine: TrackStateMachine?
    private let recorder: LocalRecorder?
    var liveBroadcastStreamer: SRTBroadcastStreamer?
    private let timestampCorrector: MonotonicTimestampCorrector?
    private let maskRenderer: PrivacyMaskRenderer?
    private let metadataRepository: MetadataFrameWriting?
    private let sessionFileCoordinator: SessionFileCoordinator?
    private let syncMonitor: AudioVideoSyncMonitor?
    private let ownerStore: OwnerEmbeddingStore?
    private let stopCoordinator = SessionStopCoordinator()
    private let imagePreprocessor = ImagePreprocessor.shared

    // MARK: - Runtime
    private var sessionID: String?
    private var frameIndex: Int = 0
    private var previewFrameIndex: Int = 0
    private var activeRecordingURL: URL?
    private var pendingManualOwnerTrackIDs: Set<Int> = []
    private var manualOwnerBindings: [Int: ManualOwnerBinding] = [:]
    private var manualOwnerRegistrationLastAttemptAt: [Int: Date] = [:]
    private var soleOwnerVisibilityLock: SoleOwnerVisibilityLock?
    private var hasDeliveredLiveBroadcastFirstVideoFrame = false

    init(
        frameProcessor: FocusFrameProcessor,
        detector: YuNetDetecting,
        tdmmInferencer: Facial3DMMInferring,
        arcFaceExtractor: ArcFaceEmbeddingExtracting? = nil,
        tracker: FaceTracker,
        stateMachine: TrackStateMachine? = nil,
        recorder: LocalRecorder? = nil,
        timestampCorrector: MonotonicTimestampCorrector? = nil,
        maskRenderer: PrivacyMaskRenderer? = nil,
        metadataRepository: MetadataFrameWriting? = nil,
        sessionFileCoordinator: SessionFileCoordinator? = nil,
        syncMonitor: AudioVideoSyncMonitor? = nil,
        ownerStore: OwnerEmbeddingStore? = nil
    ) {
        self.frameProcessor = frameProcessor
        self.detector = detector
        self.tdmmInferencer = tdmmInferencer
        self.arcFaceExtractor = arcFaceExtractor
        self.tracker = tracker
        self.stateMachine = stateMachine
        self.recorder = recorder
        self.timestampCorrector = timestampCorrector
        self.maskRenderer = maskRenderer
        self.metadataRepository = metadataRepository
        self.sessionFileCoordinator = sessionFileCoordinator
        self.syncMonitor = syncMonitor
        self.ownerStore = ownerStore
    }

    // MARK: - Session Lifecycle
    func start(sessionID: String) throws {
        lock.lock()
        defer { lock.unlock() }

        guard state == .idle || state == .stopped else {
            throw PipelineError.alreadyRunning
        }

        self.sessionID = sessionID
        self.frameIndex = 0
        self.activeRecordingURL = nil
        self.hasDeliveredLiveBroadcastFirstVideoFrame = false
        metadataRepository?.startSession(sessionID: sessionID)
        timestampCorrector?.reset()
        syncMonitor?.reset()
        stopCoordinator.reset()

        transition(to: .running)
    }

    func stop(completion: (() -> Void)? = nil) {
        guard stopCoordinator.beginStopping() else {
            completion?()
            return
        }

        transition(to: .stopping)

        DispatchQueue.global(qos: .utility).async { [weak self] in
            guard let self else { return }

            _ = self.stopCoordinator.waitForAsyncWork(group: self.asyncGroup)

            self.finishSessionArtifacts { outputs in
                self.lock.lock()
                self.sessionID = nil
                self.frameIndex = 0
                self.activeRecordingURL = nil
                self.lock.unlock()

                self.transition(to: .stopped)
                DispatchQueue.main.async {
                    self.onSessionFinished?(outputs)
                    completion?()
                }
            }
        }
    }

    // MARK: - Input
    func processVideoSampleBuffer(_ sampleBuffer: CMSampleBuffer) {
        lock.lock()
        let currentState = state
        let currentSessionID = sessionID
        if currentState == .running {
            frameIndex += 1
        }
        let currentFrameIndex = frameIndex
        lock.unlock()

        guard currentState == .running,
              let currentSessionID,
              let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
            return
        }

        let pts = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
        let ptsUs: Int64

        if let timestampCorrector {
            ptsUs = timestampCorrector.correctedPTSUs(from: pts)
        } else {
            ptsUs = Int64(CMTimeGetSeconds(pts) * FocusConstants.ptsScaleMicroseconds)
        }

        let context = FrameContext(
            pixelBuffer: pixelBuffer,
            videoSampleBuffer: sampleBuffer,
            pts: pts,
            ptsUs: ptsUs,
            sessionID: currentSessionID,
            frameIndex: currentFrameIndex,
            mode: .recording
        )

        inferenceQueue.async { [weak self] in
            self?.runSyncPipeline(context: context)
        }
    }

    func processPreviewSampleBuffer(_ sampleBuffer: CMSampleBuffer) {
        lock.lock()
        let currentState = state
        guard currentState != .running && currentState != .stopping else {
            lock.unlock()
            return
        }
        previewFrameIndex += 1
        let currentFrameIndex = previewFrameIndex
        lock.unlock()

        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
            return
        }

        let pts = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
        let ptsUs = Int64(CMTimeGetSeconds(pts) * FocusConstants.ptsScaleMicroseconds)
        let context = FrameContext(
            pixelBuffer: pixelBuffer,
            videoSampleBuffer: nil,
            pts: pts,
            ptsUs: ptsUs,
            sessionID: nil,
            frameIndex: currentFrameIndex,
            mode: .preview
        )

        inferenceQueue.async { [weak self] in
            self?.runSyncPipeline(context: context)
        }
    }

    func processAudioSampleBuffer(_ sampleBuffer: CMSampleBuffer) {
        lock.lock()
        let currentState = state
        lock.unlock()

        guard currentState == .running else { return }

        syncMonitor?.recordAudioPTS(CMSampleBufferGetPresentationTimeStamp(sampleBuffer))

        encoderQueue.async { [weak self] in
            if let liveBroadcastStreamer = self?.liveBroadcastStreamer {
                liveBroadcastStreamer.appendAudio(sampleBuffer)
            } else {
                self?.recorder?.appendAudioSampleBuffer(sampleBuffer)
            }
        }
    }

    func requestManualOwnerRegistration(trackID: Int) {
        lock.lock()
        pendingManualOwnerTrackIDs.insert(trackID)
        lock.unlock()
    }

    func bindExistingOwner(ownerID: UUID, to trackID: Int) {
        stateMachine?.removeTrack(trackID)

        lock.lock()
        pendingManualOwnerTrackIDs.remove(trackID)
        manualOwnerRegistrationLastAttemptAt[trackID] = nil
        manualOwnerBindings[trackID] = ManualOwnerBinding(
            ownerID: ownerID,
            lastUpgradeAt: Date()
        )
        lock.unlock()

        var tracks = tracker.tracks
        var didUpdate = false
        for index in tracks.indices where tracks[index].trackID == trackID {
            tracks[index].label = .owner
            tracks[index].ownerID = ownerID
            didUpdate = true
        }

        if didUpdate {
            tracker.replaceTracks(with: tracks)
        }
    }

    func removeManualOwner(ownerID: UUID) {
        stateMachine?.removeOwner(ownerID: ownerID)

        lock.lock()
        manualOwnerBindings = manualOwnerBindings.filter { $0.value.ownerID != ownerID }
        if soleOwnerVisibilityLock?.ownerID == ownerID {
            soleOwnerVisibilityLock = nil
        }
        lock.unlock()

        forceTracksToOther(ownerID: ownerID, trackID: nil)
    }

    func removeOwner(ownerID: UUID?, trackID: Int) {
        let shouldNotifyOwnerStoreChanged = ownerID != nil

        if let ownerID {
            _ = ownerStore?.removeOwner(ownerID: ownerID)
        }

        stateMachine?.removeTrack(trackID)

        lock.lock()
        pendingManualOwnerTrackIDs.remove(trackID)
        manualOwnerBindings.removeValue(forKey: trackID)
        manualOwnerRegistrationLastAttemptAt[trackID] = nil
        if let ownerID {
            manualOwnerBindings = manualOwnerBindings.filter { $0.value.ownerID != ownerID }
            if soleOwnerVisibilityLock?.ownerID == ownerID {
                soleOwnerVisibilityLock = nil
            }
        }
        lock.unlock()

        _ = forceTracksToOther(ownerID: ownerID, trackID: trackID)

        if shouldNotifyOwnerStoreChanged {
            notifyOwnerStoreChanged()
        }
    }

    func resetAnalysisState(clearManualOwnerBindings: Bool = true) {
        frameProcessor.reset()

        lock.lock()
        frameIndex = 0
        previewFrameIndex = 0
        hasDeliveredLiveBroadcastFirstVideoFrame = false

        if clearManualOwnerBindings {
            pendingManualOwnerTrackIDs.removeAll()
            manualOwnerBindings.removeAll()
        } else {
            pendingManualOwnerTrackIDs.removeAll()
        }

        manualOwnerRegistrationLastAttemptAt.removeAll()
        soleOwnerVisibilityLock = nil
        lock.unlock()
    }

    // MARK: - Pipeline Core
    private func runSyncPipeline(context: FrameContext) {
        do {
            let processedFrame = try frameProcessor.process(
                pixelBuffer: context.pixelBuffer,
                timestampMs: context.ptsUs / 1_000,
                frameIndex: context.frameIndex
            )
            var trackedFaces = processedFrame.trackedFaces

            applyManualOwnerOverrides(&trackedFaces)
            processManualOwnerRegistrationsIfNeeded(
                trackedFaces: &trackedFaces,
                detections: processedFrame.detections,
                tdmmList: processedFrame.tdmmList,
                pixelBuffer: context.pixelBuffer
            )
            applySoleOwnerExclusionIfNeeded(&trackedFaces)
            tracker.replaceTracks(with: trackedFaces)
            let presentationTracks = DuplicateFaceFilter.dedupeTracks(
                trackedFaces.filter { $0.missedFrames <= FocusConstants.previewOverlayMaxMissedFrames }
            )
            let recordingMaskTracks = DuplicateFaceFilter.dedupeTracks(
                trackedFaces.filter { $0.missedFrames <= FocusConstants.recordingMaskMaxMissedFrames }
            ).map { track in
                guard track.missedFrames > 0 else { return track }
                var fallbackTrack = track
                fallbackTrack.landmarks = nil
                return fallbackTrack
            }

            let metadataFaceCount: Int
            if context.mode == .recording, let sessionID = context.sessionID {
                metadataFaceCount = metadataRepository?.appendFrame(
                    sessionID: sessionID,
                    ptsUs: context.ptsUs,
                    tracks: trackedFaces
                ) ?? 0
            } else {
                metadataFaceCount = 0
            }

            DispatchQueue.main.async { [weak self] in
                self?.onPreviewFrame?(
                    context.pixelBuffer,
                    presentationTracks,
                    processedFrame.detections,
                    recordingMaskTracks
                )
            }

            if context.mode == .recording, let sessionID = context.sessionID {
                dispatchLabelRefineIfNeeded(trackedFaces: trackedFaces)
                dispatchOwnerTaskIfNeeded(trackedFaces: trackedFaces)

                syncMonitor?.recordVideoPTS(context.pts)
                appendRecordingFrameIfNeeded(
                    pixelBuffer: context.pixelBuffer,
                    sourceSampleBuffer: context.videoSampleBuffer,
                    tracks: recordingMaskTracks,
                    pts: context.pts,
                    sessionID: sessionID
                )
            }

            let snapshot = PipelineDebugSnapshot(
                frameIndex: context.frameIndex,
                detectedFaceCount: processedFrame.detections.count,
                trackedFaceCount: trackedFaces.count,
                metadataFaceCount: metadataFaceCount,
                ptsUs: context.ptsUs
            )

            DispatchQueue.main.async { [weak self] in
                self?.onDebugSnapshot?(snapshot)
            }
        } catch {
            FocusLogger.error(
                "pipeline error: \(error.localizedDescription)",
                category: .pipeline
            )
        }
    }

    private func enrichLabelsIfNeeded(
        trackedFaces: inout [TrackedFace],
        detections: [DetectedFace],
        tdmmList: [TDMMCoefficients?],
        pixelBuffer: CVPixelBuffer,
        stateMachine: TrackStateMachine,
        arcFaceExtractor: ArcFaceEmbeddingExtracting
    ) {
        if !trackedFaces.isEmpty {
            stateMachine.beginFrame(
                seenTrackIDs: Set(
                    trackedFaces
                        .filter { $0.missedFrames == 0 }
                        .map(\.trackID)
                )
            )
        }

        for index in trackedFaces.indices {
            if trackedFaces[index].missedFrames == 0, trackedFaces[index].tdmm != nil {
                stateMachine.recordFrameSeen(trackID: trackedFaces[index].trackID)
            }
            stateMachine.applyState(to: &trackedFaces[index])

            let isFrontal = Self.isFrontalFace(landmarks: trackedFaces[index].landmarks)

            guard let matchedDetection = bestMatchingDetection(
                for: trackedFaces[index],
                from: detections,
                tdmmList: tdmmList
            ) else {
                continue
            }

            if stateMachine.shouldCollectEmbedding(for: trackedFaces[index]) && isFrontal {
                do {
                    let embedding = try arcFaceExtractor.extractEmbedding(
                        from: pixelBuffer,
                        face: matchedDetection
                    )
                    stateMachine.updateLabel(
                        track: &trackedFaces[index],
                        newEmbedding: embedding,
                        isFrontal: true
                    )
                } catch {
                    FocusLogger.warning(
                        "embedding collect error: \(error.localizedDescription)",
                        category: .inference
                    )
                }
            } else if stateMachine.shouldRetryOther(for: trackedFaces[index], isFrontal: isFrontal) {
                do {
                    let embedding = try arcFaceExtractor.extractEmbedding(
                        from: pixelBuffer,
                        face: matchedDetection
                    )
                    stateMachine.updateLabel(
                        track: &trackedFaces[index],
                        newEmbedding: embedding,
                        isFrontal: true
                    )
                } catch {
                    FocusLogger.warning(
                        "embedding retry error: \(error.localizedDescription)",
                        category: .inference
                    )
                }
            }
        }

        tracker.replaceTracks(with: trackedFaces)
    }

    private func bestMatchingDetection(
        for trackedFace: TrackedFace,
        from detections: [DetectedFace],
        tdmmList: [TDMMCoefficients?] = []
    ) -> DetectedFace? {
        var bestDetectionIndex: Int?
        var bestCost = Float.greatestFiniteMagnitude

        for detectionIndex in detections.indices {
            let detectionTDMM = tdmmList.indices.contains(detectionIndex) ? tdmmList[detectionIndex] : nil
            guard let candidate = TrackCost.combinedCost(
                track: trackedFace,
                detection: detections[detectionIndex],
                detectionTDMM: detectionTDMM
            ) else {
                continue
            }

            if candidate.cost < bestCost {
                bestCost = candidate.cost
                bestDetectionIndex = detectionIndex
            }
        }

        if let bestDetectionIndex {
            return detections[bestDetectionIndex]
        }

        return detections.max { lhs, rhs in
            intersectionOverUnion(lhs.bbox, trackedFace.bbox) < intersectionOverUnion(rhs.bbox, trackedFace.bbox)
        }
    }

    private func intersectionOverUnion(_ lhs: CGRect, _ rhs: CGRect) -> CGFloat {
        let intersection = lhs.intersection(rhs)
        if intersection.isNull || intersection.isEmpty { return 0 }

        let intersectionArea = intersection.width * intersection.height
        let unionArea = lhs.width * lhs.height + rhs.width * rhs.height - intersectionArea
        guard unionArea > 0 else { return 0 }
        return intersectionArea / unionArea
    }

    private func currentPresentationTracks() -> [TrackedFace] {
        let visibleTracks = tracker.tracks
            .filter { $0.missedFrames <= FocusConstants.previewOverlayMaxMissedFrames }
        return DuplicateFaceFilter.dedupeTracks(visibleTracks)
    }

    private func currentRecordingMaskTracks() -> [TrackedFace] {
        let maskTracks = tracker.tracks
            .filter { $0.missedFrames <= FocusConstants.recordingMaskMaxMissedFrames }
        return DuplicateFaceFilter.dedupeTracks(maskTracks).map { track in
            guard track.missedFrames > 0 else { return track }
            var fallbackTrack = track
            fallbackTrack.landmarks = nil
            return fallbackTrack
        }
    }

    // MARK: - Async Branches
    private func dispatchLabelRefineIfNeeded(trackedFaces: [TrackedFace]) {
        labelRefineQueue.async(group: asyncGroup) {
            _ = trackedFaces
            // TODO:
            // 원본 재디코드 기반 라벨 보정
            // Owner 강등 금지 병합 규칙 유지
        }
    }

    private func dispatchOwnerTaskIfNeeded(trackedFaces: [TrackedFace]) {
        ownerTaskQueue.async(group: asyncGroup) {
            _ = trackedFaces
            // TODO:
            // 수동 Owner 등록 / 업그레이드 큐
            // throttle 적용
        }
    }

    // MARK: - Recording / Finalize
    private func appendRecordingFrameIfNeeded(
        pixelBuffer: CVPixelBuffer,
        sourceSampleBuffer: CMSampleBuffer?,
        tracks: [TrackedFace],
        pts: CMTime,
        sessionID: String
    ) {
        let recordingPixelBuffer: CVPixelBuffer
        if let maskRenderer {
            if shouldMaskRecordingFaces,
               let maskedPixelBuffer = maskRenderer.makeMaskedPixelBuffer(from: pixelBuffer, tracks: tracks) {
                recordingPixelBuffer = maskedPixelBuffer
            } else if let copiedPixelBuffer = maskRenderer.copyPixelBuffer(pixelBuffer) {
                recordingPixelBuffer = copiedPixelBuffer
            } else {
                recordingPixelBuffer = pixelBuffer
            }
        } else {
            recordingPixelBuffer = pixelBuffer
        }

        if let liveBroadcastStreamer,
           let sourceSampleBuffer,
           let videoSampleBuffer = VideoSampleBufferFactory.makeSampleBuffer(
                from: recordingPixelBuffer,
                timingSource: sourceSampleBuffer
           ) {
            if !hasDeliveredLiveBroadcastFirstVideoFrame {
                hasDeliveredLiveBroadcastFirstVideoFrame = true
                DispatchQueue.main.async { [weak self] in
                    self?.onLiveBroadcastFirstVideoFrame?()
                }
            }
            encoderQueue.async {
                liveBroadcastStreamer.appendVideo(videoSampleBuffer)
            }
            return
        }

        guard let recorder else { return }

        prepareRecorderIfNeeded(
            recorder: recorder,
            pixelBuffer: pixelBuffer,
            sessionID: sessionID
        )

        encoderQueue.async {
            recorder.appendVideoPixelBuffer(recordingPixelBuffer, pts: pts)
        }
    }

    private func prepareRecorderIfNeeded(
        recorder: LocalRecorder,
        pixelBuffer: CVPixelBuffer,
        sessionID: String
    ) {
        guard recorder.currentOutputURL == nil,
              let sessionFileCoordinator else {
            return
        }

        do {
            let outputURL = try sessionFileCoordinator.makeRecordingOutputURL(sessionID: sessionID)
            try recorder.prepareRecording(
                outputURL: outputURL,
                videoSize: CGSize(
                    width: CVPixelBufferGetWidth(pixelBuffer),
                    height: CVPixelBufferGetHeight(pixelBuffer)
                )
            )

            lock.lock()
            activeRecordingURL = outputURL
            lock.unlock()
        } catch {
            FocusLogger.error(
                "녹화 준비 실패: \(error.localizedDescription)",
                category: .streaming
            )
        }
    }

    private func finishSessionArtifacts(completion: @escaping (PipelineSessionOutputs) -> Void) {
        let metadataURL: URL?
        do {
            metadataURL = try metadataRepository?.finishSession()
        } catch {
            FocusLogger.error(
                "metadata 저장 실패: \(error.localizedDescription)",
                category: .metadata
            )
            metadataURL = nil
        }

        let recordingURL: URL?
        let finishedSessionID: String?
        lock.lock()
        recordingURL = activeRecordingURL
        finishedSessionID = sessionID
        lock.unlock()

        let finalizeOutputs: () -> Void = { [weak self] in
            guard let self else { return }

            Task {
                let avatarArtifacts = await self.exportAvatarArtifactsIfPossible(
                    sessionID: finishedSessionID,
                    recordingURL: recordingURL,
                    metadataURL: metadataURL
                )

                completion(
                    PipelineSessionOutputs(
                        recordingURL: recordingURL,
                        metadataURL: metadataURL,
                        avatarVideoURL: avatarArtifacts?.videoURL,
                        avatarSchemaURL: avatarArtifacts?.schemaURL
                    )
                )
            }
        }

        guard let recorder else {
            finalizeOutputs()
            return
        }

        recorder.finishWriting {
            finalizeOutputs()
        }
    }

    private func exportAvatarArtifactsIfPossible(
        sessionID: String?,
        recordingURL: URL?,
        metadataURL: URL?
    ) async -> AvatarDeliveryArtifacts? {
        guard let sessionID,
              let recordingURL,
              let metadataURL,
              let sessionFileCoordinator else {
            return nil
        }

        do {
            let exporter = AvatarDebugSchemaExporter(fileCoordinator: sessionFileCoordinator)
            return try await exporter.export(
                sessionID: sessionID,
                recordingURL: recordingURL,
                metadataURL: metadataURL
            )
        } catch {
            FocusLogger.error(
                "avatar schema 저장 실패: \(error.localizedDescription)",
                category: .metadata
            )
            return nil
        }
    }

    // MARK: - Helpers
    private func applyManualOwnerOverrides(_ trackedFaces: inout [TrackedFace]) {
        reassignManualOwnerBindingsIfNeeded(in: trackedFaces)
        pruneManualOwnerState(validTrackIDs: Set(trackedFaces.map(\.trackID)))

        lock.lock()
        let pendingTrackIDs = pendingManualOwnerTrackIDs
        let boundOwnerIDsByTrackID = manualOwnerBindings.mapValues(\.ownerID)
        let boundTrackIDs = Set(manualOwnerBindings.keys)
        lock.unlock()

        for index in trackedFaces.indices {
            if pendingTrackIDs.contains(trackedFaces[index].trackID) || boundTrackIDs.contains(trackedFaces[index].trackID) {
                trackedFaces[index].label = .owner
                if let ownerID = boundOwnerIDsByTrackID[trackedFaces[index].trackID] {
                    trackedFaces[index].ownerID = ownerID
                }
            }
        }
    }

    private func applySoleOwnerExclusionIfNeeded(_ trackedFaces: inout [TrackedFace]) {
        updateSoleOwnerVisibilityLock(using: trackedFaces)

        lock.lock()
        let currentLock = soleOwnerVisibilityLock
        lock.unlock()

        guard let currentLock,
              currentLock.isActive else {
            return
        }

        for index in trackedFaces.indices {
            if let lockedTrackID = currentLock.lockedTrackID,
               trackedFaces[index].trackID == lockedTrackID {
                trackedFaces[index].label = .owner
                trackedFaces[index].ownerID = currentLock.ownerID
                continue
            }

            trackedFaces[index].label = .other
            trackedFaces[index].ownerID = nil
            trackedFaces[index].frontalEmbeddingSamples.removeAll()
            trackedFaces[index].hasRetriedOther = true
        }
    }

    private func updateSoleOwnerVisibilityLock(using trackedFaces: [TrackedFace]) {
        guard let ownerStore else {
            lock.lock()
            soleOwnerVisibilityLock = nil
            lock.unlock()
            return
        }

        let owners = ownerStore.allOwners()
        guard owners.count == 1 else {
            lock.lock()
            soleOwnerVisibilityLock = nil
            lock.unlock()
            return
        }

        let soleOwnerID = owners[0].id
        let visibleTracks = trackedFaces.filter { $0.missedFrames == 0 }

        lock.lock()
        let bindingsByTrackID = manualOwnerBindings
        let pendingTrackIDs = pendingManualOwnerTrackIDs
        lock.unlock()

        let boundTrackID = bindingsByTrackID.first { $0.value.ownerID == soleOwnerID }?.key
        let hasCompetingPendingRegistration = pendingTrackIDs.contains { trackID in
            if trackID == boundTrackID { return false }
            return bindingsByTrackID[trackID]?.ownerID != soleOwnerID
        }

        guard !hasCompetingPendingRegistration else {
            lock.lock()
            soleOwnerVisibilityLock = nil
            lock.unlock()
            return
        }

        lock.lock()
        let existingLock = soleOwnerVisibilityLock
        lock.unlock()

        var nextLock = existingLock?.ownerID == soleOwnerID
            ? existingLock
            : SoleOwnerVisibilityLock(
                ownerID: soleOwnerID,
                lockedTrackID: nil,
                lastLockedTrack: nil,
                confirmedFrames: 0,
                missingFrames: 0
            )

        if let preservedTrack = preservedLockedTrack(
            from: nextLock,
            visibleTracks: visibleTracks
        ) {
            let currentConfirmedFrames = nextLock?.confirmedFrames ?? 0
            nextLock?.lockedTrackID = preservedTrack.trackID
            nextLock?.lastLockedTrack = preservedTrack
            nextLock?.confirmedFrames = max(
                currentConfirmedFrames,
                FocusConstants.soleOwnerLockConfirmFrames
            )
            nextLock?.missingFrames = 0
            lock.lock()
            soleOwnerVisibilityLock = nextLock
            lock.unlock()
            return
        }

        if let explicitTrack = explicitSoleOwnerCandidate(
            ownerID: soleOwnerID,
            boundTrackID: boundTrackID,
            visibleTracks: visibleTracks
        ) {
            let confirmedFrames: Int
            if explicitTrack.trackID == boundTrackID {
                confirmedFrames = FocusConstants.soleOwnerLockConfirmFrames
            } else if nextLock?.lockedTrackID == explicitTrack.trackID {
                confirmedFrames = min(
                    (nextLock?.confirmedFrames ?? 0) + 1,
                    FocusConstants.soleOwnerLockConfirmFrames + 1
                )
            } else {
                confirmedFrames = min(
                    max(nextLock?.confirmedFrames ?? 0, 0) + 1,
                    FocusConstants.soleOwnerLockConfirmFrames
                )
            }

            nextLock?.lockedTrackID = explicitTrack.trackID
            nextLock?.lastLockedTrack = explicitTrack
            nextLock?.confirmedFrames = confirmedFrames
            nextLock?.missingFrames = 0
            lock.lock()
            soleOwnerVisibilityLock = nextLock
            lock.unlock()
            return
        }

        if let transferredTrack = transferredSoleOwnerCandidate(
            from: nextLock,
            excluding: pendingTrackIDs,
            visibleTracks: visibleTracks
        ) {
            let currentConfirmedFrames = nextLock?.confirmedFrames ?? 0
            nextLock?.lockedTrackID = transferredTrack.trackID
            nextLock?.lastLockedTrack = transferredTrack
            nextLock?.confirmedFrames = max(
                currentConfirmedFrames,
                FocusConstants.soleOwnerLockConfirmFrames
            )
            nextLock?.missingFrames = 0
            lock.lock()
            soleOwnerVisibilityLock = nextLock
            lock.unlock()
            return
        }

        guard var existingLock = nextLock else {
            lock.lock()
            soleOwnerVisibilityLock = nil
            lock.unlock()
            return
        }

        existingLock.lockedTrackID = nil
        existingLock.missingFrames += 1

        if existingLock.missingFrames > FocusConstants.soleOwnerLockGraceFrames {
            lock.lock()
            soleOwnerVisibilityLock = nil
            lock.unlock()
        } else {
            lock.lock()
            soleOwnerVisibilityLock = existingLock
            lock.unlock()
        }
    }

    private func preservedLockedTrack(
        from lock: SoleOwnerVisibilityLock?,
        visibleTracks: [TrackedFace]
    ) -> TrackedFace? {
        guard let lockedTrackID = lock?.lockedTrackID else {
            return nil
        }

        return visibleTracks.first { $0.trackID == lockedTrackID }
    }

    private func explicitSoleOwnerCandidate(
        ownerID: UUID,
        boundTrackID: Int?,
        visibleTracks: [TrackedFace]
    ) -> TrackedFace? {
        if let boundTrackID,
           let boundTrack = visibleTracks.first(where: { $0.trackID == boundTrackID }) {
            return boundTrack
        }

        return visibleTracks
            .filter { $0.ownerID == ownerID }
            .sorted(by: soleOwnerCandidatePriority)
            .first
    }

    private func transferredSoleOwnerCandidate(
        from lock: SoleOwnerVisibilityLock?,
        excluding pendingTrackIDs: Set<Int>,
        visibleTracks: [TrackedFace]
    ) -> TrackedFace? {
        guard let sourceTrack = lock?.lastLockedTrack else {
            return nil
        }

        var bestTrack: TrackedFace?
        var bestCost = Float.greatestFiniteMagnitude

        for track in visibleTracks {
            guard !pendingTrackIDs.contains(track.trackID) else {
                continue
            }

            let candidateDetection = DetectedFace(
                bbox: track.bbox,
                landmarks: track.landmarks,
                confidence: 1
            )

            guard let candidate = TrackCost.combinedCost(
                track: sourceTrack,
                detection: candidateDetection,
                detectionTDMM: track.tdmm
            ),
            candidate.cost <= FocusConstants.soleOwnerLockTransferMaxCost else {
                continue
            }

            if candidate.cost < bestCost {
                bestCost = candidate.cost
                bestTrack = track
            }
        }

        return bestTrack
    }

    private func soleOwnerCandidatePriority(_ lhs: TrackedFace, _ rhs: TrackedFace) -> Bool {
        if lhs.label != rhs.label {
            return lhs.label == .owner
        }

        if lhs.framesSeen != rhs.framesSeen {
            return lhs.framesSeen > rhs.framesSeen
        }

        if lhs.age != rhs.age {
            return lhs.age > rhs.age
        }

        return (lhs.bbox.width * lhs.bbox.height) > (rhs.bbox.width * rhs.bbox.height)
    }

    private func reassignManualOwnerBindingsIfNeeded(in trackedFaces: [TrackedFace]) {
        lock.lock()
        defer { lock.unlock() }

        guard !pendingManualOwnerTrackIDs.isEmpty || !manualOwnerBindings.isEmpty else {
            return
        }

        let latchedTrackIDs = trackedFaces.compactMap { track -> Int? in
            if pendingManualOwnerTrackIDs.contains(track.trackID) || manualOwnerBindings[track.trackID] != nil {
                return track.trackID
            }
            return nil
        }

        guard !latchedTrackIDs.isEmpty else { return }

        var transferredTargetIDs = Set<Int>()

        for sourceTrackID in latchedTrackIDs {
            guard let sourceTrack = trackedFaces.first(where: { $0.trackID == sourceTrackID }) else {
                continue
            }

            var bestTargetTrackID: Int?
            var bestScore: CGFloat = -.greatestFiniteMagnitude

            for targetTrack in trackedFaces {
                guard targetTrack.trackID != sourceTrack.trackID,
                      !pendingManualOwnerTrackIDs.contains(targetTrack.trackID),
                      manualOwnerBindings[targetTrack.trackID] == nil,
                      !transferredTargetIDs.contains(targetTrack.trackID),
                      targetTrack.missedFrames == 0,
                      sourceTrack.missedFrames > targetTrack.missedFrames,
                      isLikelyDuplicateTrackedFace(sourceTrack, other: targetTrack) else {
                    continue
                }

                let score = duplicateTransferScore(source: sourceTrack, target: targetTrack)
                if score > bestScore {
                    bestScore = score
                    bestTargetTrackID = targetTrack.trackID
                }
            }

            guard let bestTargetTrackID else { continue }

            if pendingManualOwnerTrackIDs.remove(sourceTrackID) != nil {
                pendingManualOwnerTrackIDs.insert(bestTargetTrackID)
            }

            if let binding = manualOwnerBindings.removeValue(forKey: sourceTrackID) {
                manualOwnerBindings[bestTargetTrackID] = binding
            }

            if let lastAttempt = manualOwnerRegistrationLastAttemptAt.removeValue(forKey: sourceTrackID) {
                manualOwnerRegistrationLastAttemptAt[bestTargetTrackID] = lastAttempt
            }

            transferredTargetIDs.insert(bestTargetTrackID)
        }
    }

    private func processManualOwnerRegistrationsIfNeeded(
        trackedFaces: inout [TrackedFace],
        detections: [DetectedFace],
        tdmmList: [TDMMCoefficients?],
        pixelBuffer: CVPixelBuffer
    ) {
        let visibleTrackCount = trackedFaces.filter { $0.missedFrames == 0 }.count

        for index in trackedFaces.indices {
            let track = trackedFaces[index]

            lock.lock()
            let isPendingManualOwner = pendingManualOwnerTrackIDs.contains(track.trackID)
            let binding = manualOwnerBindings[track.trackID]
            lock.unlock()

            if isPendingManualOwner {
                registerManualOwnerIfPossible(
                    track: &trackedFaces[index],
                    detections: detections,
                    tdmmList: tdmmList,
                    pixelBuffer: pixelBuffer
                )
            } else if let binding {
                upgradeManualOwnerIfPossible(
                    track: &trackedFaces[index],
                    detections: detections,
                    tdmmList: tdmmList,
                    pixelBuffer: pixelBuffer,
                    binding: binding,
                    visibleTrackCount: visibleTrackCount
                )
            }
        }
    }

    private func registerManualOwnerIfPossible(
        track: inout TrackedFace,
        detections: [DetectedFace],
        tdmmList: [TDMMCoefficients?],
        pixelBuffer: CVPixelBuffer
    ) {
        guard shouldRetryManualOwnerRegistration(
            for: track.trackID,
            intervalMs: FocusConstants.ownerRegistrationRetryIntervalMs
        ) else {
            return
        }

        guard track.bbox.width >= FocusConstants.minManualOwnerFaceSize,
              track.bbox.height >= FocusConstants.minManualOwnerFaceSize,
              Self.isFrontalFace(landmarks: track.landmarks),
              let arcFaceExtractor,
              let ownerStore,
              let matchedDetection = bestMatchingDetection(for: track, from: detections, tdmmList: tdmmList) else {
            return
        }

        do {
            let embedding = try arcFaceExtractor.extractEmbedding(from: pixelBuffer, face: matchedDetection)
            let ownerRecord = ownerStore.addOwner(embedding: embedding)
            let snapshotURL = try? persistOwnerSnapshot(
                from: pixelBuffer,
                rect: matchedDetection.bbox,
                identifier: ownerRecord.id.uuidString
            )

            if let snapshotURL {
                _ = ownerStore.replaceOwnerEmbedding(
                    ownerID: ownerRecord.id,
                    embedding: embedding,
                    snapshotURL: snapshotURL
                )
            }

            stateMachine?.removeTrack(track.trackID)

            lock.lock()
            pendingManualOwnerTrackIDs.remove(track.trackID)
            manualOwnerRegistrationLastAttemptAt[track.trackID] = nil
            manualOwnerBindings[track.trackID] = ManualOwnerBinding(
                ownerID: ownerRecord.id,
                lastUpgradeAt: Date()
            )
            lock.unlock()

            track.label = .owner
            track.ownerID = ownerRecord.id
            notifyOwnerStoreChanged()
        } catch {
            lock.lock()
            manualOwnerRegistrationLastAttemptAt[track.trackID] = Date()
            lock.unlock()

            FocusLogger.warning(
                "수동 owner 등록 실패(track \(track.trackID)): \(error.localizedDescription)",
                category: .pipeline
            )
        }
    }

    private func upgradeManualOwnerIfPossible(
        track: inout TrackedFace,
        detections: [DetectedFace],
        tdmmList: [TDMMCoefficients?],
        pixelBuffer: CVPixelBuffer,
        binding: ManualOwnerBinding,
        visibleTrackCount: Int
    ) {
        guard track.bbox.width >= FocusConstants.minManualOwnerFaceSize,
              track.bbox.height >= FocusConstants.minManualOwnerFaceSize,
              Self.isFrontalFace(landmarks: track.landmarks),
              let arcFaceExtractor,
              let ownerStore,
              let matchedDetection = bestMatchingDetection(for: track, from: detections, tdmmList: tdmmList),
              shouldRetryManualOwnerUpgrade(binding: binding, intervalMs: FocusConstants.ownerUpgradeRetryIntervalMs) else {
            return
        }

        do {
            let embedding = try arcFaceExtractor.extractEmbedding(from: pixelBuffer, face: matchedDetection)
            guard shouldUpgradeManualOwner(
                ownerID: binding.ownerID,
                with: embedding,
                visibleTrackCount: visibleTrackCount,
                ownerStore: ownerStore
            ) else {
                return
            }

            let snapshotURL = try? persistOwnerSnapshot(
                from: pixelBuffer,
                rect: matchedDetection.bbox,
                identifier: binding.ownerID.uuidString
            )

            _ = ownerStore.replaceOwnerEmbedding(
                ownerID: binding.ownerID,
                embedding: embedding,
                snapshotURL: snapshotURL
            )

            lock.lock()
            manualOwnerBindings[track.trackID]?.lastUpgradeAt = Date()
            lock.unlock()

            track.label = .owner
            track.ownerID = binding.ownerID
            notifyOwnerStoreChanged()
        } catch {
            FocusLogger.warning(
                "owner 임베딩 업그레이드 실패(track \(track.trackID)): \(error.localizedDescription)",
                category: .pipeline
            )
        }
    }

    private func shouldRetryManualOwnerRegistration(for trackID: Int, intervalMs: Int) -> Bool {
        lock.lock()
        let lastAttemptAt = manualOwnerRegistrationLastAttemptAt[trackID]
        lock.unlock()

        guard let lastAttemptAt else { return true }
        return Date().timeIntervalSince(lastAttemptAt) * 1_000.0 >= Double(intervalMs)
    }

    private func shouldRetryManualOwnerUpgrade(binding: ManualOwnerBinding, intervalMs: Int) -> Bool {
        guard let lastUpgradeAt = binding.lastUpgradeAt else { return true }
        return Date().timeIntervalSince(lastUpgradeAt) * 1_000.0 >= Double(intervalMs)
    }

    private func pruneManualOwnerState(validTrackIDs: Set<Int>) {
        lock.lock()
        manualOwnerRegistrationLastAttemptAt = manualOwnerRegistrationLastAttemptAt.reduce(into: [:]) { partialResult, entry in
            guard pendingManualOwnerTrackIDs.contains(entry.key) || manualOwnerBindings[entry.key] != nil else {
                return
            }
            partialResult[entry.key] = entry.value
        }
        lock.unlock()
    }

    private func isLikelyDuplicateTrackedFace(_ lhs: TrackedFace, other rhs: TrackedFace) -> Bool {
        let iou = CGFloat(TrackCost.intersectionOverUnion(lhs.bbox, rhs.bbox))
        if iou >= 0.55 {
            return true
        }

        let lhsCenter = CGPoint(x: lhs.bbox.midX, y: lhs.bbox.midY)
        let rhsCenter = CGPoint(x: rhs.bbox.midX, y: rhs.bbox.midY)
        let dx = lhsCenter.x - rhsCenter.x
        let dy = lhsCenter.y - rhsCenter.y
        let centerDistance = sqrt(dx * dx + dy * dy)
        let minSide = max(1, min(lhs.bbox.width, lhs.bbox.height, rhs.bbox.width, rhs.bbox.height))

        return iou >= 0.35 && centerDistance <= minSide * 0.28
    }

    private func duplicateTransferScore(source: TrackedFace, target: TrackedFace) -> CGFloat {
        let iou = CGFloat(TrackCost.intersectionOverUnion(source.bbox, target.bbox))
        let sourceCenter = CGPoint(x: source.bbox.midX, y: source.bbox.midY)
        let targetCenter = CGPoint(x: target.bbox.midX, y: target.bbox.midY)
        let dx = sourceCenter.x - targetCenter.x
        let dy = sourceCenter.y - targetCenter.y
        let distance = sqrt(dx * dx + dy * dy)
        let scale = max(1, min(source.bbox.width, source.bbox.height, target.bbox.width, target.bbox.height))
        return (iou * 10.0) - (distance / scale)
    }

    private func shouldUpgradeManualOwner(
        ownerID: UUID,
        with embedding: [Float],
        visibleTrackCount: Int,
        ownerStore: OwnerEmbeddingStore
    ) -> Bool {
        guard visibleTrackCount <= 1 else {
            return false
        }

        guard let ownerRecord = ownerStore.allOwners().first(where: { $0.id == ownerID }) else {
            return false
        }

        let normalizedEmbedding = l2Normalize(embedding)
        guard !normalizedEmbedding.isEmpty else {
            return false
        }

        let bestSimilarity = ownerRecord.embeddings.reduce(Float(-1)) { currentBest, storedEmbedding in
            let normalizedStoredEmbedding = l2Normalize(storedEmbedding)
            guard !normalizedStoredEmbedding.isEmpty else {
                return currentBest
            }

            let similarity = cosineSimilarity(normalizedEmbedding, normalizedStoredEmbedding)
            return max(currentBest, similarity)
        }

        return bestSimilarity >= FocusConstants.ownerUpgradeSimilarityThreshold
    }

    private func l2Normalize(_ vector: [Float]) -> [Float] {
        guard !vector.isEmpty else { return [] }

        let norm = sqrt(vector.reduce(Float(0)) { partialResult, value in
            partialResult + (value * value)
        })

        guard norm > 0 else { return [] }
        return vector.map { $0 / norm }
    }

    private func cosineSimilarity(_ lhs: [Float], _ rhs: [Float]) -> Float {
        guard lhs.count == rhs.count, !lhs.isEmpty else { return -1 }

        var dot: Float = 0
        for index in lhs.indices {
            dot += lhs[index] * rhs[index]
        }

        return dot
    }

    private func persistOwnerSnapshot(
        from pixelBuffer: CVPixelBuffer,
        rect: CGRect,
        identifier: String
    ) throws -> URL {
        guard let sessionFileCoordinator else {
            throw InferenceError.preprocessingFailed("snapshot 저장 경로를 만들 수 없습니다.")
        }

        let outputURL = try sessionFileCoordinator.makeOwnerSnapshotURL(identifier: identifier)
        let jpegData = try imagePreprocessor.jpegData(
            from: pixelBuffer,
            rect: rect,
            rotationDegrees: FocusConstants.ownerSnapshotRotationDegrees
        )
        try jpegData.write(to: outputURL, options: .atomic)
        return outputURL
    }

    private func notifyOwnerStoreChanged() {
        DispatchQueue.main.async { [weak self] in
            self?.onOwnerStoreChanged?()
        }
    }

    @discardableResult
    private func forceTracksToOther(ownerID: UUID?, trackID: Int?) -> Bool {
        var tracks = tracker.tracks
        var didUpdate = false

        for index in tracks.indices {
            let sameTrack = (trackID != nil && tracks[index].trackID == trackID)
            let sameOwner = (ownerID != nil && tracks[index].ownerID == ownerID)
            guard sameTrack || sameOwner else { continue }

            tracks[index].label = .other
            tracks[index].ownerID = nil
            tracks[index].frontalEmbeddingSamples.removeAll()
            tracks[index].hasRetriedOther = true
            didUpdate = true
        }

        if didUpdate {
            tracker.replaceTracks(with: tracks)
        }

        return didUpdate
    }

    private func transition(to newState: PipelineState) {
        DispatchQueue.main.async { [weak self] in
            self?.state = newState
            self?.onStateChanged?(newState)
        }
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
