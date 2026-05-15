//
//  protoMessageBuilder.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation
import CoreGraphics

final class ProtoMessageBuilder {
    struct BuildResult {
        let message: Focus_FrameMetadata
        let includedTrackIDs: [Int]
        let dropped: [(trackID: Int, reason: MetadataDropReason)]
    }

    func buildFrameMetadata(
        sessionID: String,
        ptsUs: Int64,
        tracks: [TrackedFace]
    ) -> BuildResult {
        var frame = Focus_FrameMetadata()
        frame.sessionID = sessionID
        frame.ptsUs = ptsUs

        var includedTrackIDs: [Int] = []
        var dropped: [(trackID: Int, reason: MetadataDropReason)] = []

        for track in tracks {
            let policy = MetadataPolicy.evaluate(track: track)
            guard policy.shouldInclude else {
                if let reason = policy.reason {
                    dropped.append((track.trackID, reason))
                }
                continue
            }

            guard let tdmm = track.tdmm else {
                dropped.append((track.trackID, .missingTDMM))
                continue
            }

            let face = makeFaceData(trackID: track.trackID, bbox: track.bbox, tdmm: tdmm)
            frame.faces.append(face)
            includedTrackIDs.append(track.trackID)
        }

        return BuildResult(
            message: frame,
            includedTrackIDs: includedTrackIDs,
            dropped: dropped
        )
    }

    private func makeFaceData(
        trackID: Int,
        bbox: CGRect,
        tdmm: TDMMCoefficients
    ) -> Focus_FaceData {
        var face = Focus_FaceData()
        face.trackingID = Int32(trackID)
        face.bbox = makeBBox(bbox)
        face.tdmmRaw = makeTDMMRaw(tdmm)
        return face
    }

    private func makeBBox(_ rect: CGRect) -> Focus_BBox {
        let values = MetadataPolicy.clampBBoxToInt(rect)
        var bbox = Focus_BBox()
        bbox.x = values.x
        bbox.y = values.y
        bbox.width = values.width
        bbox.height = values.height
        return bbox
    }

    private func makeTDMMRaw(_ tdmm: TDMMCoefficients) -> Focus_ThreeDMMRaw {
        var raw = Focus_ThreeDMMRaw()
        raw.coeffs = tdmm.merged
        return raw
    }
}
