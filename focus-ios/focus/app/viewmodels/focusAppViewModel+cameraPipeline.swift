//
//  focusAppViewModel+cameraPipeline.swift
//  focus
//
//  Created by Codex on 4/11/26.
//

import SwiftUI
import AVFoundation

extension FocusAppViewModel {
    func startPreviewIfPossible() {
        cameraManager.startRunning()
        isCameraRunning = true
    }

    func setupCameraRouter() {
        cameraManager.router.onVideoSample = { [weak self] sampleBuffer in
            guard let self else { return }

            if let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) {
                let sourceSize = CGSize(
                    width: CVPixelBufferGetWidth(pixelBuffer),
                    height: CVPixelBufferGetHeight(pixelBuffer)
                )

                Task { @MainActor in
                    self.previewRuntime.latestPixelBuffer = pixelBuffer
                    self.previewSourceSize = sourceSize
                }
            }

            if let pipelineController = self.pipelineController {
                if self.isRunning {
                    pipelineController.processVideoSampleBuffer(sampleBuffer)
                } else {
                    pipelineController.processPreviewSampleBuffer(sampleBuffer)
                }
            } else {
                Task { @MainActor in
                    self.processedFrameCount += 1
                }
            }
        }

        cameraManager.router.onAudioSample = { [weak self] sampleBuffer in
            guard let self else { return }
            guard self.isRunning else { return }
            self.pipelineController?.processAudioSampleBuffer(sampleBuffer)
        }
    }

    func buildPipelineIfPossible() {
        guard pipelineController == nil else { return }

        do {
            let detector = try YuNetOpenCVService(
                modelFileName: "face_detection_yunet_2023mar",
                modelFileExtension: "onnx",
                inputSize: 360,
                scoreThreshold: FocusConstants.yunetScoreThreshold,
                nmsThreshold: 0.3,
                topK: 5000
            )

            let tdmmInferencer: Facial3DMMInferring
            do {
                tdmmInferencer = try Facial3DMMTFLiteService(
                    modelFileName: "facemap_3dmm-facial-landmark-detection-float",
                    modelFileExtension: "tflite"
                )
            } catch {
                tdmmInferencer = NoOpFacial3DMMService()
            }

            let arcFaceExtractor = try? ArcFaceONNXService()

            let tracker = previewRuntime.faceTracker
            let stateMachine = previewRuntime.trackStateMachine
            let frameProcessor = FocusFrameProcessor(
                detector: detector,
                tdmmInferencer: tdmmInferencer,
                tracker: tracker,
                stateMachine: stateMachine,
                arcFaceExtractor: arcFaceExtractor
            )
            let pipeline = FocusPipelineController(
                frameProcessor: frameProcessor,
                detector: detector,
                tdmmInferencer: tdmmInferencer,
                arcFaceExtractor: arcFaceExtractor,
                tracker: tracker,
                stateMachine: stateMachine,
                recorder: recorder,
                timestampCorrector: timestampCorrector,
                maskRenderer: PrivacyMaskRenderer(),
                metadataRepository: metadataRepository,
                sessionFileCoordinator: fileCoordinator,
                syncMonitor: syncMonitor,
                ownerStore: ownerStore
            )
            pipeline.shouldMaskRecordingFaces = privacyMode != .disabled

            pipeline.onDebugSnapshot = { [weak self] (snapshot: PipelineDebugSnapshot) in
                guard let self else { return }
                Task { @MainActor in
                    self.processedFrameCount = snapshot.frameIndex
                }
            }

            pipeline.onPreviewFrame = { [weak self] (
                pixelBuffer: CVPixelBuffer,
                trackedFaces: [TrackedFace],
                detectedFaces: [DetectedFace],
                maskTracks: [TrackedFace]
            ) in
                guard let self else { return }
                Task { @MainActor in
                    self.previewTrackedFaces = self.sanitizedTracksForCurrentOwners(trackedFaces)
                    self.previewDetectedFaces = detectedFaces
                    self.previewMaskTracks = maskTracks
                    self.previewSourceSize = CGSize(
                        width: CVPixelBufferGetWidth(pixelBuffer),
                        height: CVPixelBufferGetHeight(pixelBuffer)
                    )
                }
            }

            pipeline.onSessionFinished = { [weak self] (outputs: PipelineSessionOutputs) in
                guard let self else { return }
                Task { @MainActor in
                    let finishedSessionID = self.sessionID
                    self.isRecording = false
                    self.metadataConnected = false
                    self.sessionID = nil
                    self.lastRecordingURL = outputs.recordingURL
                    self.lastMetadataURL = outputs.metadataURL
                    self.lastAvatarVideoURL = outputs.avatarVideoURL
                    self.lastAvatarSchemaURL = outputs.avatarSchemaURL
                    if outputs.avatarVideoURL != nil || outputs.avatarSchemaURL != nil {
                        self.showStatus("아바타 전달용 가로 영상과 JSON이 저장되었어요.")
                    }

                    if let recordingURL = outputs.recordingURL {
                        await self.saveRecordingToPhotoLibrary(recordingURL)
                    }

                    await self.stopRemoteBroadcastIfNeeded()

                    if let finishedSessionID {
                        await self.closeRemoteSessionIfNeeded(sessionID: finishedSessionID)
                    }

                    await self.presentPostStreamReport(from: outputs)
                }
            }

            pipeline.onOwnerStoreChanged = { [weak self] in
                self?.refreshOwnerProfiles(showSuccessIfPending: true)
            }

            pipeline.onLiveBroadcastFirstVideoFrame = { [weak self] in
                Task { @MainActor in
                    self?.confirmPreparedRemoteBroadcastStartIfNeeded()
                }
            }

            self.pipelineController = pipeline
        } catch {
            handleError("파이프라인 생성 실패: \(error.localizedDescription)")
        }
    }

    func refreshOwnerProfiles(showSuccessIfPending: Bool = false) {
        let previousCount = ownerProfiles.count
        let summaries = ownerStore.summaries()
        ownerProfiles = summaries

        if showSuccessIfPending,
           pendingOwnerRegistrationFeedback,
           summaries.count > previousCount {
            pendingOwnerRegistrationFeedback = false
            pendingOwnerFeedbackTask?.cancel()
            showStatus("오너 등록이 완료되었습니다.")
        }
    }

    func clearPreviewTrackingState() {
        previewTrackedFaces.removeAll()
        previewDetectedFaces.removeAll()
        previewMaskTracks.removeAll()
        previewSourceSize = .zero
        pipelineController?.resetAnalysisState()
        previewRuntime.reset()
    }

    func previewFaceOverlays(for previewSize: CGSize) -> [PreviewFaceOverlay] {
        let visibleTracks = overlayPreviewTracks()
        guard !visibleTracks.isEmpty else { return [] }

        let rects = previewRuntime.hitTester.mappedTrackRects(
            tracks: visibleTracks,
            previewSize: previewSize,
            sourceSize: previewSourceSize,
            isMirrored: false
        )

        let labelByTrackID: [Int: TrackLabel] = Dictionary(
            uniqueKeysWithValues: visibleTracks.map { ($0.trackID, $0.label) }
        )

        return rects.compactMap { rect in
            guard let label = labelByTrackID[rect.trackID] else { return nil }
            return PreviewFaceOverlay(trackID: rect.trackID, rect: rect.rect, label: label)
        }
    }

    func previewDebugOverlays(for previewSize: CGSize) -> [PreviewDebugOverlay] {
        guard isDebugVisionOverlayEnabled,
              previewSourceSize.width > 0,
              previewSourceSize.height > 0 else {
            return []
        }

        let isMirrored = false
        var overlays: [PreviewDebugOverlay] = []

        for (index, detectedFace) in previewDetectedFaces.enumerated() {
            guard let rect = previewRuntime.hitTester.mapRectToPreview(
                detectedFace.bbox,
                previewSize: previewSize,
                sourceSize: previewSourceSize,
                isMirrored: isMirrored
            ) else {
                continue
            }

            overlays.append(
                PreviewDebugOverlay(
                    id: "det-\(index)",
                    rect: rect,
                    kind: .detector,
                    title: String(format: "D %.2f", detectedFace.confidence)
                )
            )
        }

        for track in overlayPreviewTracks() {
            guard let rect = previewRuntime.hitTester.mapRectToPreview(
                track.bbox,
                previewSize: previewSize,
                sourceSize: previewSourceSize,
                isMirrored: isMirrored
            ) else {
                continue
            }

            overlays.append(
                PreviewDebugOverlay(
                    id: "trk-\(track.trackID)",
                    rect: rect,
                    kind: .tracker,
                    title: "T \(track.trackID)"
                )
            )
        }

        for track in previewMaskTracks {
            let maskRect = PrivacyMaskRenderer.debugMaskRect(for: track)
            guard let rect = previewRuntime.hitTester.mapRectToPreview(
                maskRect,
                previewSize: previewSize,
                sourceSize: previewSourceSize,
                isMirrored: isMirrored
            ) else {
                continue
            }

            overlays.append(
                PreviewDebugOverlay(
                    id: "mask-\(track.trackID)",
                    rect: rect,
                    kind: .mask,
                    title: "M \(track.trackID)"
                )
            )
        }

        return overlays
    }

}
