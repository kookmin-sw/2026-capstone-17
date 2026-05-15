//
//  audioVideoSyncMonitor.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation
import CoreMedia

final class AudioVideoSyncMonitor {
    struct Snapshot: Sendable {
        let lastVideoPTSUs: Int64?
        let lastAudioPTSUs: Int64?
        let driftMs: Double?
        let isWithinTolerance: Bool
    }

    private let lock = NSLock()
    private var lastVideoPTSUs: Int64?
    private var lastAudioPTSUs: Int64?

    func recordVideoPTS(_ pts: CMTime) {
        let ptsUs = Int64(CMTimeGetSeconds(pts) * FocusConstants.ptsScaleMicroseconds)
        lock.lock()
        lastVideoPTSUs = ptsUs
        lock.unlock()
    }

    func recordAudioPTS(_ pts: CMTime) {
        let ptsUs = Int64(CMTimeGetSeconds(pts) * FocusConstants.ptsScaleMicroseconds)
        lock.lock()
        lastAudioPTSUs = ptsUs
        lock.unlock()
    }

    func snapshot() -> Snapshot {
        lock.lock()
        defer { lock.unlock() }

        guard let v = lastVideoPTSUs, let a = lastAudioPTSUs else {
            return Snapshot(
                lastVideoPTSUs: lastVideoPTSUs,
                lastAudioPTSUs: lastAudioPTSUs,
                driftMs: nil,
                isWithinTolerance: true
            )
        }

        let driftUs = abs(v - a)
        let driftMs = Double(driftUs) / 1000.0

        return Snapshot(
            lastVideoPTSUs: v,
            lastAudioPTSUs: a,
            driftMs: driftMs,
            isWithinTolerance: driftMs <= FocusConstants.avDriftToleranceMs
        )
    }

    func reset() {
        lock.lock()
        lastVideoPTSUs = nil
        lastAudioPTSUs = nil
        lock.unlock()
    }
}
