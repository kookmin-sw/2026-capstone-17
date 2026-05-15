//
//  cameraPreviewView.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import SwiftUI
import AVFoundation

struct CameraPreviewView: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewContainerView {
        let view = PreviewContainerView()
        configurePreviewLayer(view.previewLayer)
        return view
    }

    func updateUIView(_ uiView: PreviewContainerView, context: Context) {
        configurePreviewLayer(uiView.previewLayer)
    }

    private func configurePreviewLayer(_ previewLayer: AVCaptureVideoPreviewLayer) {
        previewLayer.session = session
        previewLayer.videoGravity = .resizeAspectFill

        guard let connection = previewLayer.connection else { return }

        if connection.isVideoMirroringSupported {
            connection.automaticallyAdjustsVideoMirroring = false
            connection.isVideoMirrored = isUsingFrontCamera
        }
    }

    private var isUsingFrontCamera: Bool {
        session.inputs
            .compactMap { $0 as? AVCaptureDeviceInput }
            .contains { $0.device.position == .front }
    }
}

struct PortraitLockedCameraPreview: View {
    let session: AVCaptureSession
    let rotationDegrees: Double

    init(session: AVCaptureSession, rotationDegrees: Double = -90) {
        self.session = session
        self.rotationDegrees = rotationDegrees
    }

    var body: some View {
        GeometryReader { geometry in
            CameraPreviewView(session: session)
                .frame(width: geometry.size.height, height: geometry.size.width)
                .rotationEffect(.degrees(rotationDegrees))
                .position(x: geometry.size.width / 2, y: geometry.size.height / 2)
        }
        .clipped()
    }
}

final class PreviewContainerView: UIView {
    override class var layerClass: AnyClass {
        AVCaptureVideoPreviewLayer.self
    }

    var previewLayer: AVCaptureVideoPreviewLayer {
        guard let layer = self.layer as? AVCaptureVideoPreviewLayer else {
            fatalError("Preview layer casting failed.")
        }
        return layer
    }
}
