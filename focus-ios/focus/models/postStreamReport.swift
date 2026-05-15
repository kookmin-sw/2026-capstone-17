//
//  postStreamReport.swift
//  focus
//
//  Created by Codex on 5/6/26.
//

import Foundation

enum PostStreamAnalysisStatus: String, Equatable, Sendable {
    case processing = "PROCESSING"
    case succeeded = "SUCCEEDED"
    case failed = "FAILED"

    var title: String {
        switch self {
        case .processing:
            return "분석 중"
        case .succeeded:
            return "분석 완료"
        case .failed:
            return "분석 실패"
        }
    }
}

struct PostStreamContentRatio: Identifiable, Equatable, Sendable {
    let id = UUID()
    let contentType: String
    let percentage: Double
    let durationSec: Int
}

struct PostStreamHighlightMoment: Identifiable, Equatable, Sendable {
    let id = UUID()
    let timeLabel: String
    let title: String
    let description: String
}

struct PostStreamAnalysisReport: Identifiable, Equatable, Sendable {
    let id: String
    let title: String
    let broadcastID: String
    let analysisJobID: String
    let durationSec: Int
    let analysisStatus: PostStreamAnalysisStatus
    let summary: String
    let strengths: [String]
    let weaknesses: [String]
    let actionItems: [String]
    let totalReplacedFaceCount: Int
    let maxSimultaneousCrowdCount: Int
    let highlightCount: Int
    let peakViewerCount: Int
    let peakOccurredAtLabel: String?
    let peakSceneDescription: String
    let contentRatios: [PostStreamContentRatio]
    let highlightMoments: [PostStreamHighlightMoment]
    let generatedAt: Date
    let recordingURL: URL?
}
