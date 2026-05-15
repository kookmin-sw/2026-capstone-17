//
//  avatarDebugSchemaExporter.swift
//  focus
//
//  Created by Codex on 5/15/26.
//

import Foundation
import AVFoundation
import CoreGraphics

struct AvatarDeliveryArtifacts: Sendable {
    let videoURL: URL
    let schemaURL: URL
}

struct AvatarVideoSchema: Encodable {
    let schema_version: String
    let session_id: String
    let video: AvatarVideoDescriptor
    let frames: [AvatarVideoFrame]
}

struct AvatarVideoDescriptor: Encodable {
    let file_name: String
    let width: Int
    let height: Int
    let fps: Double
    let duration_ms: Int64
    let coordinate_origin: String
    let is_mirrored: Bool
    let rotation: Int
}

struct AvatarVideoFrame: Encodable {
    let frame_index: Int
    let pts_us: Int64
    let pts_us_from_start: Int64
    let faces: [AvatarVideoFace]
}

struct AvatarVideoFace: Encodable {
    let tracking_id: Int
    let bbox: AvatarVideoBBox
    let bbox_normalized: AvatarVideoNormalizedBBox
    let label: String
    let tdmm_raw: AvatarVideoTDMMRaw?
}

struct AvatarVideoBBox: Encodable {
    let x: Int
    let y: Int
    let width: Int
    let height: Int
}

struct AvatarVideoNormalizedBBox: Encodable {
    let x: Double
    let y: Double
    let width: Double
    let height: Double
}

struct AvatarVideoTDMMRaw: Encodable {
    let coeffs: [Float]
}

final class AvatarDebugSchemaExporter {
    private struct MetadataRoot: Decodable {
        let frames: [MetadataFrame]
    }

    private struct MetadataFrame: Decodable {
        let pts_us: Int64
        let faces: [MetadataFace]
    }

    private struct MetadataFace: Decodable {
        let tracking_id: Int
        let bbox: MetadataBBox
        let tdmm_raw: MetadataTDMMRaw?
    }

    private struct MetadataBBox: Decodable {
        let x: Int
        let y: Int
        let width: Int
        let height: Int
    }

    private struct MetadataTDMMRaw: Decodable {
        let coeffs: [Float]
    }

    private enum DeliveryFormat {
        static let width = 1280
        static let height = 720
        static let rotationDegrees = 270
        static let schemaVersion = "1.1"
    }

    private let fileCoordinator: SessionFileCoordinator
    private let decoder = JSONDecoder()
    private let coordinateTransformer = CoordinateTransformer()
    private let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return encoder
    }()

    init(fileCoordinator: SessionFileCoordinator) {
        self.fileCoordinator = fileCoordinator
    }

    func export(
        sessionID: String,
        recordingURL: URL,
        metadataURL: URL
    ) async throws -> AvatarDeliveryArtifacts {
        let metadataData = try Data(contentsOf: metadataURL)
        let metadataRoot = try decoder.decode(MetadataRoot.self, from: metadataData)

        let sourceDescriptor = try await makeSourceVideoDescriptor(recordingURL: recordingURL)
        let avatarVideoURL = try fileCoordinator.makeAvatarVideoOutputURL(sessionID: sessionID)
        try await exportLandscapeVideo(
            sourceURL: recordingURL,
            destinationURL: avatarVideoURL,
            sourceVideoSize: CGSize(width: sourceDescriptor.width, height: sourceDescriptor.height)
        )

        let outputDescriptor = try await makeOutputVideoDescriptor(recordingURL: avatarVideoURL)
        let outputSize = CGSize(width: outputDescriptor.width, height: outputDescriptor.height)
        let firstPTS = metadataRoot.frames.first?.pts_us ?? 0

        let frames = metadataRoot.frames.enumerated().map { index, frame in
            AvatarVideoFrame(
                frame_index: index,
                pts_us: frame.pts_us,
                pts_us_from_start: frame.pts_us - firstPTS,
                faces: frame.faces.compactMap { face in
                    let transformedRect = transformBBox(
                        face.bbox,
                        sourceDescriptor: sourceDescriptor
                    )
                    let clampedRect = coordinateTransformer.clampRect(
                        transformedRect,
                        to: outputSize
                    )

                    guard clampedRect.width > 0, clampedRect.height > 0 else {
                        return nil
                    }

                    return AvatarVideoFace(
                        tracking_id: face.tracking_id,
                        bbox: AvatarVideoBBox(
                            x: Int(clampedRect.origin.x.rounded()),
                            y: Int(clampedRect.origin.y.rounded()),
                            width: Int(clampedRect.width.rounded()),
                            height: Int(clampedRect.height.rounded())
                        ),
                        bbox_normalized: AvatarVideoNormalizedBBox(
                            x: normalizedValue(clampedRect.origin.x, max: outputSize.width),
                            y: normalizedValue(clampedRect.origin.y, max: outputSize.height),
                            width: normalizedValue(clampedRect.width, max: outputSize.width),
                            height: normalizedValue(clampedRect.height, max: outputSize.height)
                        ),
                        label: "other",
                        tdmm_raw: face.tdmm_raw.map { AvatarVideoTDMMRaw(coeffs: $0.coeffs) }
                    )
                }
            )
        }

        let schema = AvatarVideoSchema(
            schema_version: DeliveryFormat.schemaVersion,
            session_id: sessionID,
            video: outputDescriptor,
            frames: frames
        )

        let data = try encoder.encode(schema)
        let outputSchemaURL = try fileCoordinator.makeAvatarSchemaOutputURL(sessionID: sessionID)
        try data.write(to: outputSchemaURL, options: Data.WritingOptions.atomic)

        return AvatarDeliveryArtifacts(
            videoURL: avatarVideoURL,
            schemaURL: outputSchemaURL
        )
    }

    private func makeSourceVideoDescriptor(recordingURL: URL) async throws -> AvatarVideoDescriptor {
        let asset = AVURLAsset(url: recordingURL)
        let duration = try await asset.load(.duration)
        let videoTracks = try await asset.loadTracks(withMediaType: .video)
        let videoTrack = videoTracks.first
        let naturalSize = try await videoTrack?.load(.naturalSize) ?? .zero
        let nominalFrameRate = try await videoTrack?.load(.nominalFrameRate) ?? 0

        return AvatarVideoDescriptor(
            file_name: recordingURL.lastPathComponent,
            width: Int(abs(naturalSize.width).rounded()),
            height: Int(abs(naturalSize.height).rounded()),
            fps: nominalFrameRate > 0 ? Double(nominalFrameRate) : 30.0,
            duration_ms: Int64((duration.seconds * 1_000).rounded()),
            coordinate_origin: "top_left",
            is_mirrored: false,
            rotation: 0
        )
    }

    private func makeOutputVideoDescriptor(recordingURL: URL) async throws -> AvatarVideoDescriptor {
        let asset = AVURLAsset(url: recordingURL)
        let duration = try await asset.load(.duration)
        let videoTracks = try await asset.loadTracks(withMediaType: .video)
        let videoTrack = videoTracks.first
        let nominalFrameRate = try await videoTrack?.load(.nominalFrameRate) ?? 0

        return AvatarVideoDescriptor(
            file_name: recordingURL.lastPathComponent,
            width: DeliveryFormat.width,
            height: DeliveryFormat.height,
            fps: nominalFrameRate > 0 ? Double(nominalFrameRate) : 30.0,
            duration_ms: Int64((duration.seconds * 1_000).rounded()),
            coordinate_origin: "top_left",
            is_mirrored: false,
            rotation: 0
        )
    }

    private func exportLandscapeVideo(
        sourceURL: URL,
        destinationURL: URL,
        sourceVideoSize: CGSize
    ) async throws {
        if FileManager.default.fileExists(atPath: destinationURL.path) {
            try FileManager.default.removeItem(at: destinationURL)
        }

        let asset = AVURLAsset(url: sourceURL)
        guard let exportSession = AVAssetExportSession(
            asset: asset,
            presetName: AVAssetExportPreset1280x720
        ) else {
            throw NSError(
                domain: "AvatarDebugSchemaExporter",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "가로 전달용 export session을 만들지 못했습니다."]
            )
        }

        guard let videoTrack = try await asset.loadTracks(withMediaType: .video).first else {
            throw NSError(
                domain: "AvatarDebugSchemaExporter",
                code: -2,
                userInfo: [NSLocalizedDescriptionKey: "원본 비디오 트랙을 찾지 못했습니다."]
            )
        }

        let duration = try await asset.load(.duration)
        let nominalFrameRate = try await videoTrack.load(.nominalFrameRate)
        let fps = nominalFrameRate > 0 ? Int(round(nominalFrameRate)) : 30

        let videoComposition = AVMutableVideoComposition()
        videoComposition.renderSize = CGSize(
            width: DeliveryFormat.width,
            height: DeliveryFormat.height
        )
        videoComposition.frameDuration = CMTime(
            value: 1,
            timescale: CMTimeScale(max(fps, 1))
        )

        let instruction = AVMutableVideoCompositionInstruction()
        instruction.timeRange = CMTimeRange(start: .zero, duration: duration)

        let layerInstruction = AVMutableVideoCompositionLayerInstruction(assetTrack: videoTrack)
        layerInstruction.setTransform(
            makeLandscapeTransform(sourceVideoSize: sourceVideoSize),
            at: .zero
        )
        instruction.layerInstructions = [layerInstruction]
        videoComposition.instructions = [instruction]

        exportSession.outputURL = destinationURL
        exportSession.outputFileType = .mp4
        exportSession.videoComposition = videoComposition
        exportSession.shouldOptimizeForNetworkUse = true

        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            exportSession.exportAsynchronously {
                switch exportSession.status {
                case .completed:
                    continuation.resume()
                case .failed, .cancelled:
                    continuation.resume(
                        throwing: exportSession.error ?? NSError(
                            domain: "AvatarDebugSchemaExporter",
                            code: -3,
                            userInfo: [NSLocalizedDescriptionKey: "가로 전달용 영상 export에 실패했습니다."]
                        )
                    )
                default:
                    continuation.resume(
                        throwing: NSError(
                            domain: "AvatarDebugSchemaExporter",
                            code: -4,
                            userInfo: [NSLocalizedDescriptionKey: "가로 전달용 영상 export 상태가 비정상적입니다."]
                        )
                    )
                }
            }
        }
    }

    private func makeLandscapeTransform(sourceVideoSize: CGSize) -> CGAffineTransform {
        let scaleX = CGFloat(DeliveryFormat.width) / sourceVideoSize.height
        let scaleY = CGFloat(DeliveryFormat.height) / sourceVideoSize.width

        return CGAffineTransform(
            a: 0,
            b: -scaleY,
            c: scaleX,
            d: 0,
            tx: 0,
            ty: CGFloat(DeliveryFormat.height)
        )
    }

    private func transformBBox(
        _ bbox: MetadataBBox,
        sourceDescriptor: AvatarVideoDescriptor
    ) -> CGRect {
        coordinateTransformer.mapRectToPreview(
            CGRect(
                x: bbox.x,
                y: bbox.y,
                width: bbox.width,
                height: bbox.height
            ),
            config: CoordinateTransformConfig(
                sourceSize: CGSize(width: sourceDescriptor.width, height: sourceDescriptor.height),
                destinationSize: CGSize(width: DeliveryFormat.width, height: DeliveryFormat.height),
                isMirrored: false,
                rotationDegrees: DeliveryFormat.rotationDegrees
            )
        )
    }

    private func normalizedValue(_ value: CGFloat, max: CGFloat) -> Double {
        guard max > 0 else { return 0 }
        return (Double(value / max) * 1_000_000).rounded() / 1_000_000
    }
}
