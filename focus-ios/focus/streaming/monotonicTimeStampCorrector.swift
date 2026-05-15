//
//  monotonicTimeStampCorrector.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation
import CoreMedia

final class MonotonicTimestampCorrector {
    private let lock = NSLock()
    private var lastPTSUs: Int64 = -1

    func correctedPTSUs(from pts: CMTime) -> Int64 {
        let seconds = CMTimeGetSeconds(pts)
        let rawPTSUs = Int64(seconds * FocusConstants.ptsScaleMicroseconds)

        lock.lock()
        defer { lock.unlock() }

        if rawPTSUs <= lastPTSUs {
            lastPTSUs += 1
        } else {
            lastPTSUs = rawPTSUs
        }

        return lastPTSUs
    }

    func reset() {
        lock.lock()
        lastPTSUs = -1
        lock.unlock()
    }
}
