//
//  focusCameraTypes.swift
//  focus
//
//  Created by Codex on 4/11/26.
//

import Foundation
import AVFoundation

enum CameraFacing: String, CaseIterable, Identifiable {
    case front
    case back

    var id: String { rawValue }

    var title: String {
        switch self {
        case .front:
            return "전면 카메라"
        case .back:
            return "후면 카메라"
        }
    }

    var capturePosition: AVCaptureDevice.Position {
        switch self {
        case .front:
            return .front
        case .back:
            return .back
        }
    }
}

enum PrivacyMenuMode: String, CaseIterable, Identifiable {
    case avatar
    case mosaic
    case disabled

    var id: String { rawValue }

    var title: String {
        switch self {
        case .avatar:
            return "아바타"
        case .mosaic:
            return "블러"
        case .disabled:
            return "비활성화"
        }
    }
}
