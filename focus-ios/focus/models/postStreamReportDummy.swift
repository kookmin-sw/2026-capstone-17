//
//  postStreamReportDummy.swift
//  focus
//
//  Created by Codex on 5/11/26.
//

import Foundation

extension PostStreamAnalysisReport {
    static func dummy(
        sessionID: String?,
        durationSec: Int,
        recordingURL: URL?
    ) -> PostStreamAnalysisReport {
        let safeDuration = max(durationSec, 1)
        let titleSeed = sessionID.map { String($0.prefix(6)) } ?? "DEMO"

        return PostStreamAnalysisReport(
            id: "dummy-\(titleSeed)",
            title: "FOCUS 방송 리포트",
            broadcastID: "broadcast-\(titleSeed)",
            analysisJobID: "job-\(titleSeed)",
            durationSec: safeDuration,
            analysisStatus: .succeeded,
            summary: "오늘 방송은 스트리머 중심 구도가 전반적으로 잘 유지됐고, 사람 유입이 있었던 구간에서도 보호 처리가 비교적 안정적으로 동작했습니다.",
            strengths: [
                "스트리머 얼굴 인식이 전체적으로 안정적으로 유지되었습니다.",
                "혼잡 구간에서도 비대상 인물 보호 처리가 빠르게 적용되었습니다.",
                "방송 흐름을 크게 해치지 않는 수준으로 프라이버시 보호가 수행되었습니다."
            ],
            weaknesses: [
                "재진입 인물이 많은 구간에서는 추적 안정성이 일시적으로 흔들렸습니다.",
                "측면 얼굴이 빠르게 지나가는 장면에서 보호 처리 일관성이 다소 떨어졌습니다."
            ],
            actionItems: [
                "사람 유입이 많은 장소에서는 정면 구도를 조금 더 오래 유지해 보세요.",
                "이동 방송 구간에서는 카메라 흔들림을 줄이면 보호 안정성이 더 좋아질 수 있습니다.",
                "하이라이트가 몰린 구간을 중심으로 다시보기 클립을 구성해 보세요."
            ],
            totalReplacedFaceCount: 18,
            maxSimultaneousCrowdCount: 4,
            highlightCount: 3,
            peakViewerCount: 143,
            peakOccurredAtLabel: "00:18:42",
            peakSceneDescription: "야외 이동 후 시청자와의 소통이 활발해진 장면에서 반응이 가장 높았습니다.",
            contentRatios: [
                PostStreamContentRatio(contentType: "토크", percentage: 41.0, durationSec: max(Int(Double(safeDuration) * 0.41), 1)),
                PostStreamContentRatio(contentType: "이동", percentage: 27.0, durationSec: max(Int(Double(safeDuration) * 0.27), 1)),
                PostStreamContentRatio(contentType: "실시간 상호작용", percentage: 19.0, durationSec: max(Int(Double(safeDuration) * 0.19), 1)),
                PostStreamContentRatio(contentType: "대기/전환", percentage: 13.0, durationSec: max(Int(Double(safeDuration) * 0.13), 1))
            ],
            highlightMoments: [
                PostStreamHighlightMoment(timeLabel: "00:08:12", title: "첫 사람 유입 구간", description: "주변 인물 등장 이후 보호 처리가 빠르게 적용되며 방송 흐름이 자연스럽게 유지된 장면입니다."),
                PostStreamHighlightMoment(timeLabel: "00:18:42", title: "시청자 반응 피크", description: "시청자 반응이 가장 높았던 구간으로, 소통 중심 장면이 강조되었습니다."),
                PostStreamHighlightMoment(timeLabel: "00:31:05", title: "혼잡 구간 안정화", description: "동시 인원이 가장 많았던 장면으로, 추적과 보호 처리 부담이 높았던 대표 구간입니다.")
            ],
            generatedAt: Date(),
            recordingURL: recordingURL
        )
    }
}
