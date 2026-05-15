//
//  postStreamReportArchiveFixtures.swift
//  focus
//
//  Created by Codex on 5/11/26.
//

import Foundation

private struct PostStreamArchiveFixtureSeed {
    let id: String
    let title: String
    let broadcastID: String
    let analysisJobID: String
    let durationSec: Int
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
    let daysAgo: Int
}

extension PostStreamAnalysisReport {
    static func dummyArchive() -> [PostStreamAnalysisReport] {
        archiveFixtureSeeds
            .map(makeArchiveFixture)
            .sorted { $0.generatedAt > $1.generatedAt }
    }

    private static var archiveFixtureSeeds: [PostStreamArchiveFixtureSeed] {
        [
            PostStreamArchiveFixtureSeed(
                id: "archive-0508",
                title: "홍대 야외방송 회고",
                broadcastID: "broadcast-0508",
                analysisJobID: "job-0508",
                durationSec: 2286,
                summary: "유동 인구가 많은 구간에서도 스트리머 중심 구도가 비교적 잘 유지되었고, 보호 처리도 전반적으로 안정적으로 동작했습니다.",
                strengths: ["복수 인원이 등장한 장면에서도 보호 처리가 빠르게 적용되었습니다.", "정면 구도 유지 구간에서 오너 인식 안정성이 높았습니다."],
                weaknesses: ["재진입 인물이 많은 구간에서 트래킹 안정성이 잠시 흔들렸습니다."],
                actionItems: ["혼잡한 장소에서는 카메라 회전을 조금 더 천천히 가져가 보세요.", "시청자 반응이 좋았던 하이라이트 구간을 짧은 클립으로 다시 활용해 보세요."],
                totalReplacedFaceCount: 24,
                maxSimultaneousCrowdCount: 5,
                highlightCount: 4,
                peakViewerCount: 186,
                peakOccurredAtLabel: "00:22:14",
                peakSceneDescription: "길거리 소통이 활발해진 장면에서 최고 시청자 수가 기록되었습니다.",
                contentRatios: [
                    PostStreamContentRatio(contentType: "야외 이동", percentage: 36.0, durationSec: 823),
                    PostStreamContentRatio(contentType: "토크", percentage: 31.0, durationSec: 708),
                    PostStreamContentRatio(contentType: "실시간 상호작용", percentage: 21.0, durationSec: 480),
                    PostStreamContentRatio(contentType: "대기/전환", percentage: 12.0, durationSec: 275)
                ],
                highlightMoments: [
                    PostStreamHighlightMoment(timeLabel: "00:09:18", title: "첫 유입 혼잡 구간", description: "보행자 유입이 늘어난 장면에서도 보호 처리가 비교적 빠르게 적용되었습니다."),
                    PostStreamHighlightMoment(timeLabel: "00:22:14", title: "시청자 반응 피크", description: "스트리머와 시청자 간 소통이 활발해지며 반응이 가장 높았던 장면입니다."),
                    PostStreamHighlightMoment(timeLabel: "00:31:40", title: "재진입 인물 집중 구간", description: "같은 인물이 여러 번 재등장하며 트래킹 부담이 높았던 대표 구간입니다.")
                ],
                daysAgo: 0
            ),
            PostStreamArchiveFixtureSeed(
                id: "archive-0507",
                title: "카페 토크 라이브 회고",
                broadcastID: "broadcast-0507",
                analysisJobID: "job-0507",
                durationSec: 1845,
                summary: "실내 중심 방송으로 얼굴 인식 안정성이 높았고, 보호 처리 빈도도 비교적 낮아 차분한 흐름으로 진행되었습니다.",
                strengths: ["실내 정면 구도 덕분에 오너 인식이 안정적으로 유지되었습니다.", "노출 위험이 높은 장면이 적어 전체적인 방송 흐름이 부드러웠습니다."],
                weaknesses: ["측면 자리 이동 시 일부 구간에서 보호 처리 전환이 늦게 반영되었습니다."],
                actionItems: ["카메라 위치를 조금 더 고정하면 장시간 토크 방송 품질이 더 좋아질 수 있습니다.", "시청자 반응이 높았던 질문 응답 구간을 다음 방송 오프닝에 재활용해 보세요."],
                totalReplacedFaceCount: 8,
                maxSimultaneousCrowdCount: 2,
                highlightCount: 3,
                peakViewerCount: 132,
                peakOccurredAtLabel: "00:14:06",
                peakSceneDescription: "질문에 즉답하며 분위기가 올라간 장면에서 시청자 수가 가장 높았습니다.",
                contentRatios: [
                    PostStreamContentRatio(contentType: "토크", percentage: 58.0, durationSec: 1070),
                    PostStreamContentRatio(contentType: "실시간 상호작용", percentage: 24.0, durationSec: 443),
                    PostStreamContentRatio(contentType: "대기/전환", percentage: 18.0, durationSec: 332)
                ],
                highlightMoments: [
                    PostStreamHighlightMoment(timeLabel: "00:05:42", title: "오프닝 안정화", description: "초반 오너 인식이 안정적으로 유지되며 방송 구도가 빠르게 자리 잡은 구간입니다."),
                    PostStreamHighlightMoment(timeLabel: "00:14:06", title: "질문 응답 피크", description: "시청자 반응이 가장 높았던 질의응답 중심 장면입니다.")
                ],
                daysAgo: 1
            ),
            PostStreamArchiveFixtureSeed(
                id: "archive-0505",
                title: "학교 축제 현장방송 회고",
                broadcastID: "broadcast-0505",
                analysisJobID: "job-0505",
                durationSec: 2714,
                summary: "군중 밀집도가 높아 보호 처리 부담이 컸지만, 주요 장면에서는 스트리머 중심 프레이밍이 유지되었습니다.",
                strengths: ["혼잡한 환경에서도 스트리머 얼굴 추적이 비교적 안정적으로 유지되었습니다.", "군중 구간에서도 비대상 인물 보호 처리가 전반적으로 적용되었습니다."],
                weaknesses: ["동시 인원이 많은 장면에서 일부 얼굴의 보호 처리 일관성이 떨어졌습니다.", "빠른 카메라 이동 구간에서 하이라이트 추출 품질이 다소 불안정했습니다."],
                actionItems: ["현장 방송 시 이동 속도를 조금 줄여 추적 안정성을 확보해 보세요.", "혼잡 구간 전후의 클립을 따로 점검해 개선 포인트를 확인해 보세요."],
                totalReplacedFaceCount: 43,
                maxSimultaneousCrowdCount: 9,
                highlightCount: 5,
                peakViewerCount: 241,
                peakOccurredAtLabel: "00:28:55",
                peakSceneDescription: "공연장 인근에서 현장 분위기가 강조된 장면에서 시청자 유입이 가장 높았습니다.",
                contentRatios: [
                    PostStreamContentRatio(contentType: "현장 이동", percentage: 42.0, durationSec: 1140),
                    PostStreamContentRatio(contentType: "공연 관찰", percentage: 26.0, durationSec: 706),
                    PostStreamContentRatio(contentType: "실시간 상호작용", percentage: 18.0, durationSec: 489),
                    PostStreamContentRatio(contentType: "대기/전환", percentage: 14.0, durationSec: 379)
                ],
                highlightMoments: [
                    PostStreamHighlightMoment(timeLabel: "00:11:24", title: "첫 혼잡 구간", description: "군중 밀집 장면에서 보호 처리 빈도가 급격히 높아진 구간입니다."),
                    PostStreamHighlightMoment(timeLabel: "00:28:55", title: "현장 반응 피크", description: "공연장 주변 분위기가 강하게 전달되며 시청자 반응이 가장 높았습니다."),
                    PostStreamHighlightMoment(timeLabel: "00:36:10", title: "재진입 추적 테스트", description: "같은 인물의 재등장 빈도가 높아 ID 유지 품질을 점검하기 좋았던 장면입니다.")
                ],
                daysAgo: 3
            ),
            PostStreamArchiveFixtureSeed(
                id: "archive-0502",
                title: "주말 산책 스트림 회고",
                broadcastID: "broadcast-0502",
                analysisJobID: "job-0502",
                durationSec: 1562,
                summary: "전반적으로 한적한 동선 덕분에 안정적인 얼굴 인식이 가능했고, 하이라이트도 비교적 명확하게 추출되었습니다.",
                strengths: ["소수 인원 환경에서 보호 처리와 오너 인식이 안정적으로 유지되었습니다.", "콘텐츠 흐름이 단순해 하이라이트 포인트가 비교적 명확했습니다."],
                weaknesses: ["강한 역광 구간에서 얼굴 인식 성능이 일시적으로 낮아졌습니다."],
                actionItems: ["역광 구간에서는 카메라 각도를 미세하게 조정해 보세요.", "산책 루트 중 반응이 좋았던 구간을 별도 쇼츠 후보로 저장해 보세요."],
                totalReplacedFaceCount: 6,
                maxSimultaneousCrowdCount: 2,
                highlightCount: 2,
                peakViewerCount: 97,
                peakOccurredAtLabel: "00:12:50",
                peakSceneDescription: "산책 중 풍경 설명이 이어진 장면에서 시청자 유지율이 가장 좋았습니다.",
                contentRatios: [
                    PostStreamContentRatio(contentType: "산책", percentage: 49.0, durationSec: 765),
                    PostStreamContentRatio(contentType: "토크", percentage: 29.0, durationSec: 453),
                    PostStreamContentRatio(contentType: "대기/전환", percentage: 22.0, durationSec: 344)
                ],
                highlightMoments: [
                    PostStreamHighlightMoment(timeLabel: "00:12:50", title: "풍경 설명 구간", description: "시청자 유지율이 높게 나타난 대표 장면입니다.")
                ],
                daysAgo: 6
            )
        ]
    }

    private static func makeArchiveFixture(from seed: PostStreamArchiveFixtureSeed) -> PostStreamAnalysisReport {
        let generatedAt = Calendar(identifier: .gregorian).date(
            byAdding: .day,
            value: -seed.daysAgo,
            to: Date()
        ) ?? Date()

        return PostStreamAnalysisReport(
            id: seed.id,
            title: seed.title,
            broadcastID: seed.broadcastID,
            analysisJobID: seed.analysisJobID,
            durationSec: seed.durationSec,
            analysisStatus: .succeeded,
            summary: seed.summary,
            strengths: seed.strengths,
            weaknesses: seed.weaknesses,
            actionItems: seed.actionItems,
            totalReplacedFaceCount: seed.totalReplacedFaceCount,
            maxSimultaneousCrowdCount: seed.maxSimultaneousCrowdCount,
            highlightCount: seed.highlightCount,
            peakViewerCount: seed.peakViewerCount,
            peakOccurredAtLabel: seed.peakOccurredAtLabel,
            peakSceneDescription: seed.peakSceneDescription,
            contentRatios: seed.contentRatios,
            highlightMoments: seed.highlightMoments,
            generatedAt: generatedAt,
            recordingURL: nil
        )
    }
}
