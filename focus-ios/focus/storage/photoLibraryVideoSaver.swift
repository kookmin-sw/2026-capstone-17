//
//  photoLibraryVideoSaver.swift
//  focus
//
//  Created by Codex on 4/8/26.
//

import Foundation
import Photos

final class PhotoLibraryVideoSaver {
    enum SaveError: LocalizedError {
        case fileNotFound
        case permissionDenied
        case unknown

        var errorDescription: String? {
            switch self {
            case .fileNotFound:
                return "저장할 녹화 파일을 찾을 수 없습니다."
            case .permissionDenied:
                return "사진 보관함 저장 권한이 거부되었습니다."
            case .unknown:
                return "사진 보관함 저장에 실패했습니다."
            }
        }
    }

    func saveVideo(at fileURL: URL) async throws {
        guard FileManager.default.fileExists(atPath: fileURL.path) else {
            throw SaveError.fileNotFound
        }

        let status = await authorizationStatus()
        guard status == .authorized || status == .limited else {
            throw SaveError.permissionDenied
        }

        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            PHPhotoLibrary.shared().performChanges({
                let request = PHAssetCreationRequest.forAsset()
                let options = PHAssetResourceCreationOptions()
                options.shouldMoveFile = false
                request.addResource(with: .video, fileURL: fileURL, options: options)
            }, completionHandler: { success, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if success {
                    continuation.resume()
                } else {
                    continuation.resume(throwing: SaveError.unknown)
                }
            })
        }
    }

    private func authorizationStatus() async -> PHAuthorizationStatus {
        let currentStatus = PHPhotoLibrary.authorizationStatus(for: .addOnly)
        guard currentStatus == .notDetermined else {
            return currentStatus
        }

        return await withCheckedContinuation { continuation in
            PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
                continuation.resume(returning: status)
            }
        }
    }
}
