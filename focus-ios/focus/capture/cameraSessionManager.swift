//
//  cameraSessionManager.swift
//  focus
//
//  Created by 이동언 on 3/7/26.
//

import Foundation
import AVFoundation
import UIKit

final class CameraSessionManager: NSObject, ObservableObject {
    // MARK: - Public Properties
    let session = AVCaptureSession()
    let router = SampleBufferRouter()

    @Published private(set) var sessionState: CaptureSessionState = .idle

    var previewLayer: AVCaptureVideoPreviewLayer {
        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        return layer
    }

    // MARK: - Private Properties
    private let sessionQueue = DispatchQueue(label: "focus.capture.sessionQueue", qos: .userInitiated)
    private let videoOutputQueue = DispatchQueue(label: "focus.capture.videoOutputQueue", qos: .userInitiated)
    private let audioOutputQueue = DispatchQueue(label: "focus.capture.audioOutputQueue", qos: .userInitiated)

    private let videoOutput = AVCaptureVideoDataOutput()
    private let audioOutput = AVCaptureAudioDataOutput()

    private var videoInput: AVCaptureDeviceInput?
    private var audioInput: AVCaptureDeviceInput?

    private var isConfigured = false
    private var desiredCameraPosition: AVCaptureDevice.Position = .front

    // MARK: - Authorization
    func requestPermissionsIfNeeded() async -> Bool {
        let cameraGranted = await requestVideoPermissionIfNeeded()
        let audioGranted = await requestAudioPermissionIfNeeded()
        return cameraGranted && audioGranted
    }

    func currentAuthorizationState() -> CaptureAuthorizationState {
        let video = AVCaptureDevice.authorizationStatus(for: .video)
        let audio = AVCaptureDevice.authorizationStatus(for: .audio)

        let statuses = [video, audio]

        if statuses.contains(.denied) { return .denied }
        if statuses.contains(.restricted) { return .restricted }
        if statuses.contains(.notDetermined) { return .notDetermined }
        return .authorized
    }

    private func requestVideoPermissionIfNeeded() async -> Bool {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        switch status {
        case .authorized:
            return true
        case .notDetermined:
            return await AVCaptureDevice.requestAccess(for: .video)
        case .denied, .restricted:
            return false
        @unknown default:
            return false
        }
    }

    private func requestAudioPermissionIfNeeded() async -> Bool {
        let status = AVCaptureDevice.authorizationStatus(for: .audio)
        switch status {
        case .authorized:
            return true
        case .notDetermined:
            return await AVCaptureDevice.requestAccess(for: .audio)
        case .denied, .restricted:
            return false
        @unknown default:
            return false
        }
    }

    // MARK: - Configure
    func configureSession(cameraPosition: AVCaptureDevice.Position = .front,
                          completion: ((Result<Void, Error>) -> Void)? = nil) {
        desiredCameraPosition = cameraPosition

        sessionQueue.async { [weak self] in
            guard let self else { return }

            DispatchQueue.main.async {
                self.sessionState = .configuring
            }

            do {
                try self.configureSessionInternal()
                self.isConfigured = true

                DispatchQueue.main.async {
                    self.sessionState = .configured
                    completion?(.success(()))
                }
            } catch {
                DispatchQueue.main.async {
                    self.sessionState = .failed(error.localizedDescription)
                    completion?(.failure(error))
                }
            }
        }
    }

    private func configureSessionInternal() throws {
        let authState = currentAuthorizationState()
        guard authState == .authorized else {
            throw CaptureError.authorizationDenied
        }

        session.beginConfiguration()
        defer {
            session.commitConfiguration()
        }

        session.sessionPreset = .high

        // 기존 입출력 정리
        for input in session.inputs {
            session.removeInput(input)
        }
        for output in session.outputs {
            session.removeOutput(output)
        }

        // Video Input
        guard let camera = bestCamera(for: desiredCameraPosition) else {
            throw CaptureError.cameraUnavailable
        }

        let videoInput = try AVCaptureDeviceInput(device: camera)
        guard session.canAddInput(videoInput) else {
            throw CaptureError.cannotAddVideoInput
        }
        session.addInput(videoInput)
        self.videoInput = videoInput

        // Audio Input
        guard let microphone = AVCaptureDevice.default(for: .audio) else {
            throw CaptureError.microphoneUnavailable
        }

        let audioInput = try AVCaptureDeviceInput(device: microphone)
        guard session.canAddInput(audioInput) else {
            throw CaptureError.cannotAddAudioInput
        }
        session.addInput(audioInput)
        self.audioInput = audioInput

        // Video Output
        videoOutput.alwaysDiscardsLateVideoFrames = true
        videoOutput.videoSettings = [
            kCVPixelBufferPixelFormatTypeKey as String: Int(kCVPixelFormatType_32BGRA)
        ]
        videoOutput.setSampleBufferDelegate(self, queue: videoOutputQueue)

        guard session.canAddOutput(videoOutput) else {
            throw CaptureError.cannotAddVideoOutput
        }
        session.addOutput(videoOutput)

        if let connection = videoOutput.connection(with: .video) {
            if connection.isVideoMirroringSupported && desiredCameraPosition == .front {
                connection.isVideoMirrored = true
            }
            if connection.isVideoOrientationSupported {
                connection.videoOrientation = .portrait
            }
        }

        // Audio Output
        audioOutput.setSampleBufferDelegate(self, queue: audioOutputQueue)

        guard session.canAddOutput(audioOutput) else {
            throw CaptureError.cannotAddAudioOutput
        }
        session.addOutput(audioOutput)

        try configureCameraDevice(camera)
    }

    private func configureCameraDevice(_ device: AVCaptureDevice) throws {
        do {
            try device.lockForConfiguration()
            defer { device.unlockForConfiguration() }

            if device.isSmoothAutoFocusSupported {
                device.isSmoothAutoFocusEnabled = true
            }

            if device.isFocusModeSupported(.continuousAutoFocus) {
                device.focusMode = .continuousAutoFocus
            }

            if device.isExposureModeSupported(.continuousAutoExposure) {
                device.exposureMode = .continuousAutoExposure
            }

            if device.isWhiteBalanceModeSupported(.continuousAutoWhiteBalance) {
                device.whiteBalanceMode = .continuousAutoWhiteBalance
            }

            if device.activeVideoMinFrameDuration.timescale != 0 {
                // 필요시 고정 fps 설정 가능
                // 지금은 시스템 기본값 유지
            }
        } catch {
            throw CaptureError.configurationFailed("카메라 설정 중 오류: \(error.localizedDescription)")
        }
    }

    private func bestCamera(for position: AVCaptureDevice.Position) -> AVCaptureDevice? {
        let discovery = AVCaptureDevice.DiscoverySession(
            deviceTypes: [
                .builtInTrueDepthCamera,
                .builtInWideAngleCamera,
                .builtInDualCamera
            ],
            mediaType: .video,
            position: position
        )

        return discovery.devices.first
            ?? AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position)
    }

    // MARK: - Session Control
    func startRunning() {
        sessionQueue.async { [weak self] in
            guard let self else { return }
            guard self.isConfigured else { return }
            guard !self.session.isRunning else { return }

            self.session.startRunning()

            DispatchQueue.main.async {
                self.sessionState = .running
            }
        }
    }

    func stopRunning() {
        sessionQueue.async { [weak self] in
            guard let self else { return }
            guard self.session.isRunning else {
                DispatchQueue.main.async {
                    self.sessionState = .stopped
                }
                return
            }

            self.session.stopRunning()

            DispatchQueue.main.async {
                self.sessionState = .stopped
            }
        }
    }

    func reconfigureCamera(position: AVCaptureDevice.Position,
                           completion: ((Result<Void, Error>) -> Void)? = nil) {
        desiredCameraPosition = position
        configureSession(cameraPosition: position, completion: completion)
    }

    func teardown() {
        stopRunning()

        sessionQueue.async { [weak self] in
            guard let self else { return }

            self.videoOutput.setSampleBufferDelegate(nil, queue: nil)
            self.audioOutput.setSampleBufferDelegate(nil, queue: nil)

            self.session.beginConfiguration()

            for input in self.session.inputs {
                self.session.removeInput(input)
            }
            for output in self.session.outputs {
                self.session.removeOutput(output)
            }

            self.session.commitConfiguration()

            self.videoInput = nil
            self.audioInput = nil
            self.isConfigured = false

            DispatchQueue.main.async {
                self.sessionState = .idle
            }
        }
    }
}

// MARK: - AVCaptureVideoDataOutputSampleBufferDelegate / AVCaptureAudioDataOutputSampleBufferDelegate
extension CameraSessionManager: AVCaptureVideoDataOutputSampleBufferDelegate, AVCaptureAudioDataOutputSampleBufferDelegate {
    func captureOutput(_ output: AVCaptureOutput,
                       didOutput sampleBuffer: CMSampleBuffer,
                       from connection: AVCaptureConnection) {
        if output === videoOutput {
            router.route(sampleBuffer: sampleBuffer, mediaType: .video)
        } else if output === audioOutput {
            router.route(sampleBuffer: sampleBuffer, mediaType: .audio)
        }
    }
}
