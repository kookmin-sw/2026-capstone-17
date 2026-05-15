//
//  focusAppViewModel.swift
//  focus
//
//  Created by Codex on 4/11/26.
//

import SwiftUI
import AVFoundation

@MainActor
final class FocusAppViewModel: ObservableObject {
    @Published var isRunning: Bool = false
    @Published var sessionID: String?
    @Published var processedFrameCount: Int = 0
    @Published var metadataConnected: Bool = false
    @Published var isRecording: Bool = false
    @Published var lastRecordingURL: URL?
    @Published var lastMetadataURL: URL?
    @Published var lastAvatarVideoURL: URL?
    @Published var lastAvatarSchemaURL: URL?
    @Published var ownerProfiles: [OwnerProfileSummary] = []
    @Published var previewTrackedFaces: [TrackedFace] = []
    @Published var previewDetectedFaces: [DetectedFace] = []
    @Published var previewMaskTracks: [TrackedFace] = []
    @Published var previewSourceSize: CGSize = .zero
    @Published var transientStatusMessage: String?
    @Published var isDebugVisionOverlayEnabled: Bool = true

    @Published var isCameraReady: Bool = false
    @Published var isCameraRunning: Bool = false
    @Published var showErrorAlert: Bool = false
    @Published var errorMessage: String?
    @Published var cameraFacing: CameraFacing = .front
    @Published var privacyMode: PrivacyMenuMode = .avatar
    @Published var completedStreamReport: PostStreamAnalysisReport?
    @Published var archivedStreamReports: [PostStreamAnalysisReport] = []
    @Published var isReportArchivePresented: Bool = false

    let cameraManager = CameraSessionManager()

    var pipelineController: FocusPipelineController?
    let recorder = LocalRecorder()
    let timestampCorrector = MonotonicTimestampCorrector()
    let fileCoordinator = SessionFileCoordinator()
    let ownerStore = OwnerEmbeddingStore()
    private let ownerClassifier = OwnerOtherClassifier()
    lazy var sessionAPIClient: SessionAPIClient? = {
        guard FocusConstants.enableRemoteSessionLifecycle,
              let baseURL = URL(string: FocusConstants.serverBaseURLString) else {
            return nil
        }

        return SessionAPIClient(baseURL: baseURL)
    }()
    lazy var metadataRepository: MetadataFrameWriting = {
        let localRepository = JSONMetadataRepository(fileCoordinator: fileCoordinator)
        return MultiplexMetadataRepository(localRepository: localRepository)
    }()
    let syncMonitor = AudioVideoSyncMonitor()
    let imagePreprocessor = ImagePreprocessor.shared
    let photoLibraryVideoSaver = PhotoLibraryVideoSaver()
    lazy var previewRuntime = PreviewAnalysisRuntime(
        ownerStore: ownerStore,
        classifier: ownerClassifier
    )
    var pendingOwnerRegistrationFeedback = false
    var pendingOwnerFeedbackTask: Task<Void, Never>?
    var statusDismissTask: Task<Void, Never>?

    var cameraSession: AVCaptureSession {
        cameraManager.session
    }

    var sessionStateText: String {
        isRunning ? "실행 중" : "중지됨"
    }

    var sessionIDText: String {
        sessionID ?? "-"
    }

    var metadataStateText: String {
        metadataConnected ? "연결됨" : "미연결"
    }

    var recordingStateText: String {
        isRecording ? "녹화 중" : "대기"
    }

    var cameraStatusText: String {
        isCameraReady ? "카메라 준비 완료" : "카메라 권한 확인 및 세션 준비 중"
    }

    var cameraRunningText: String {
        isCameraRunning ? "실행 중" : "중지됨"
    }

    init() {
        refreshOwnerProfiles()
        setupCameraRouter()
    }

    func prepareCameraIfNeeded() async {
        guard !isCameraReady else {
            startPreviewIfPossible()
            return
        }

        let granted = await cameraManager.requestPermissionsIfNeeded()
        guard granted else {
            handleError("카메라 또는 마이크 권한이 거부되었습니다.")
            return
        }

        await withCheckedContinuation { continuation in
            cameraManager.configureSession(cameraPosition: cameraFacing.capturePosition) { [weak self] result in
                guard let self else {
                    continuation.resume()
                    return
                }

                switch result {
                case .success:
                    self.isCameraReady = true
                    self.buildPipelineIfPossible()
                    self.startPreviewIfPossible()
                case .failure(let error):
                    self.handleError(error.localizedDescription)
                }
                continuation.resume()
            }
        }
    }

    func switchCamera(to facing: CameraFacing) {
        guard cameraFacing != facing else { return }

        cameraManager.reconfigureCamera(position: facing.capturePosition) { [weak self] result in
            guard let self else { return }

            switch result {
            case .success:
                self.cameraFacing = facing
                self.isCameraReady = true
                self.clearPreviewTrackingState()
                self.startPreviewIfPossible()
            case .failure(let error):
                self.handleError(error.localizedDescription)
            }
        }
    }

    func setPrivacyMode(_ mode: PrivacyMenuMode) {
        privacyMode = mode
        pipelineController?.shouldMaskRecordingFaces = mode != .disabled
    }

    func toggleSession() {
        if isRunning {
            stopSession()
        } else {
            startSession()
        }
    }

    func startSession() {
        guard isCameraReady else {
            handleError("카메라가 아직 준비되지 않았습니다.")
            return
        }

        buildPipelineIfPossible()

        guard let pipelineController else {
            handleError("파이프라인 초기화에 실패했습니다.")
            return
        }

        Task { @MainActor in
            let newSessionID = await self.resolveSessionIDForStart()
            self.resetDebugState()
            self.lastRecordingURL = nil
            self.lastMetadataURL = nil
            self.lastAvatarVideoURL = nil
            self.lastAvatarSchemaURL = nil

            do {
                try pipelineController.start(sessionID: newSessionID)
            } catch {
                self.handleError(error.localizedDescription)
                return
            }

            self.isRunning = true
            self.isRecording = true
            self.metadataConnected = true
            self.sessionID = newSessionID

            self.startPreviewIfPossible()
        }
    }

    func stopSession() {
        isRunning = false
        isRecording = false
        pipelineController?.stop()
    }

    func resetDebugState() {
        processedFrameCount = 0
        if !isRunning {
            sessionID = nil
        }
    }

    func handlePreviewTap(at location: CGPoint, previewSize: CGSize) {
        let visibleTracks = overlayPreviewTracks()
        let selectedTrack = previewTrackContainingPoint(
            to: location,
            previewSize: previewSize,
            visibleTracks: visibleTracks
        )

        guard let selectedTrack else {
            return
        }

        if selectedTrack.label == .owner {
            return
        }

        buildPipelineIfPossible()

        guard let pipelineController else {
            handleError("파이프라인 초기화에 실패했습니다.")
            return
        }

        pendingOwnerRegistrationFeedback = true
        pendingOwnerFeedbackTask?.cancel()
        previewTrackedFaces = previewTrackedFaces.map { track in
            guard track.trackID == selectedTrack.trackID else { return track }
            return previewTrackMarkedAsOwner(track, ownerID: track.ownerID)
        }
        pipelineController.requestManualOwnerRegistration(trackID: selectedTrack.trackID)
    }

    func removeOwner(ownerID: UUID) {
        _ = ownerStore.removeOwner(ownerID: ownerID)
        previewRuntime.trackStateMachine.removeOwner(ownerID: ownerID)
        pipelineController?.removeManualOwner(ownerID: ownerID)
        markPreviewTracksAsOther(ownerID: ownerID)
        refreshOwnerProfiles()
    }
}
