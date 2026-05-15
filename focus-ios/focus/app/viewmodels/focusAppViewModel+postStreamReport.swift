//
//  focusAppViewModel+postStreamReport.swift
//  focus
//
//  Created by Codex on 5/6/26.
//

import AVFoundation

extension FocusAppViewModel {
    func presentPostStreamReport(from outputs: PipelineSessionOutputs) async {
        let resolvedDuration = await resolvedDurationSec(from: outputs)
        completedStreamReport = PostStreamAnalysisReport.dummy(
            sessionID: sessionID,
            durationSec: resolvedDuration,
            recordingURL: outputs.recordingURL
        )
    }

    func dismissCompletedStreamReport() {
        completedStreamReport = nil
    }

    func presentReportArchive() {
        archivedStreamReports = PostStreamAnalysisReport.dummyArchive()
        isReportArchivePresented = true
    }

    func dismissReportArchive() {
        isReportArchivePresented = false
    }

    private func resolvedDurationSec(from outputs: PipelineSessionOutputs) async -> Int {
        if let recordingURL = outputs.recordingURL {
            let asset = AVURLAsset(url: recordingURL)
            if let duration = try? await asset.load(.duration) {
                let seconds = Int(round(duration.seconds))
                if seconds > 0 {
                    return seconds
                }
            }
        }

        if processedFrameCount > 0 {
            return max(processedFrameCount / 30, 1)
        }

        return 70
    }
}
