//
//  captureTypes.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation
import AVFoundation

enum CaptureMediaType {
    case video
    case audio
}

struct CaptureSample {
    let mediaType: CaptureMediaType
    let sampleBuffer: CMSampleBuffer
}

enum CaptureAuthorizationState: Equatable {
    case authorized
    case denied
    case restricted
    case notDetermined
}

enum CaptureSessionState: Equatable {
    case idle
    case configuring
    case configured
    case running
    case stopped
    case failed(String)
}

enum CaptureError: LocalizedError {
    case cameraUnavailable
    case microphoneUnavailable
    case cannotAddVideoInput
    case cannotAddAudioInput
    case cannotAddVideoOutput
    case cannotAddAudioOutput
    case authorizationDenied
    case configurationFailed(String)

    var errorDescription: String? {
        switch self {
        case .cameraUnavailable:
            return "카메라 장치를 찾을 수 없습니다."
        case .microphoneUnavailable:
            return "마이크 장치를 찾을 수 없습니다."
        case .cannotAddVideoInput:
            return "비디오 입력을 세션에 추가할 수 없습니다."
        case .cannotAddAudioInput:
            return "오디오 입력을 세션에 추가할 수 없습니다."
        case .cannotAddVideoOutput:
            return "비디오 출력을 세션에 추가할 수 없습니다."
        case .cannotAddAudioOutput:
            return "오디오 출력을 세션에 추가할 수 없습니다."
        case .authorizationDenied:
            return "카메라 또는 마이크 권한이 거부되었습니다."
        case .configurationFailed(let message):
            return "캡처 세션 구성 실패: \(message)"
        }
    }
}
