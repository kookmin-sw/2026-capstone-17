//
//  postStreamReportViews.swift
//  focus
//
//  Created by Codex on 5/6/26.
//

import SwiftUI

struct PostStreamReportSummarySheetView: View {
    let report: PostStreamAnalysisReport
    let avatarVideoURL: URL?
    let avatarSchemaURL: URL?
    let onClose: () -> Void

    @State private var isShareSheetPresented = false

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                capsuleHandle
                summaryHeader
                metaCards
                summarySection
                bulletSection("잘한 점", items: report.strengths, accent: ReportTheme.positive)
                bulletSection("아쉬운 점", items: report.weaknesses, accent: ReportTheme.warning)
                bulletSection("다음 방송 팁", items: report.actionItems, accent: ReportTheme.primary)
                statsRow
                if avatarVideoURL != nil || avatarSchemaURL != nil {
                    avatarDeliverySection(
                        videoURL: avatarVideoURL,
                        schemaURL: avatarSchemaURL
                    )
                }

                NavigationLink {
                    PostStreamReportDetailView(
                        report: report,
                        onClose: onClose
                    )
                } label: {
                    Text("자세히 보기")
                        .font(.system(size: 17, weight: .bold, design: .rounded))
                        .frame(maxWidth: .infinity)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 18)
                        .frame(height: 54)
                        .background(
                            LinearGradient(
                                colors: [ReportTheme.primary, ReportTheme.primaryBright],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                        .shadow(color: ReportTheme.primary.opacity(0.22), radius: 18, x: 0, y: 12)
                }
                .buttonStyle(.plain)

                Button(action: onClose) {
                    Text("닫기")
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                        .foregroundStyle(ReportTheme.text.opacity(0.74))
                        .frame(maxWidth: .infinity)
                        .frame(height: 46)
                        .background(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .fill(.white)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .stroke(ReportTheme.border, lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 22)
            .padding(.top, 12)
            .padding(.bottom, 26)
        }
        .background(ReportTheme.canvas.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .sheet(isPresented: $isShareSheetPresented) {
            let shareItems = [avatarVideoURL, avatarSchemaURL].compactMap { $0 }
            if !shareItems.isEmpty {
                FileShareSheet(items: shareItems)
            }
        }
    }

    var capsuleHandle: some View {
        Capsule()
            .fill(ReportTheme.border)
            .frame(width: 56, height: 6)
            .frame(maxWidth: .infinity)
            .padding(.top, 4)
    }

    var summaryHeader: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 14) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(report.title)
                        .font(.system(size: 26, weight: .bold, design: .rounded))
                        .foregroundStyle(ReportTheme.text)

                    Text("방송 종료 직후 확인할 수 있는 AI 요약 리포트")
                        .font(.system(size: 13, weight: .medium, design: .rounded))
                        .foregroundStyle(ReportTheme.text.opacity(0.58))
                }

                Spacer(minLength: 12)

                statusBadge(report.analysisStatus)
            }
        }
    }

    var metaCards: some View {
        HStack(spacing: 12) {
            reportInfoCard(
                icon: "clock.fill",
                title: "방송 길이",
                value: report.durationSec.durationLabel
            )

            reportInfoCard(
                icon: "checkmark.seal.fill",
                title: "분석 상태",
                value: report.analysisStatus.title
            )
        }
    }

    var summarySection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("요약")
                .font(.system(size: 15, weight: .bold, design: .rounded))
                .foregroundStyle(ReportTheme.text)

            Text(report.summary)
                .font(.system(size: 15, weight: .medium, design: .rounded))
                .foregroundStyle(ReportTheme.text.opacity(0.78))
                .lineSpacing(4)
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(.white)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(ReportTheme.border, lineWidth: 1)
        )
    }

    func bulletSection(_ title: String, items: [String], accent: Color) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.system(size: 15, weight: .bold, design: .rounded))
                .foregroundStyle(ReportTheme.text)

            VStack(alignment: .leading, spacing: 10) {
                ForEach(items, id: \.self) { item in
                    HStack(alignment: .top, spacing: 10) {
                        Circle()
                            .fill(accent)
                            .frame(width: 8, height: 8)
                            .padding(.top, 6)

                        Text(item)
                            .font(.system(size: 14, weight: .medium, design: .rounded))
                            .foregroundStyle(ReportTheme.text.opacity(0.78))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(.white)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(ReportTheme.border, lineWidth: 1)
        )
    }

    var statsRow: some View {
        HStack(spacing: 12) {
            metricCard(
                title: "보호 처리",
                value: "\(report.totalReplacedFaceCount)",
                subtitle: "얼굴 수"
            )
            metricCard(
                title: "최대 혼잡도",
                value: "\(report.maxSimultaneousCrowdCount)",
                subtitle: "동시 인원"
            )
            metricCard(
                title: "하이라이트",
                value: "\(report.highlightCount)",
                subtitle: "추천 구간"
            )
        }
    }

    func avatarDeliverySection(videoURL: URL?, schemaURL: URL?) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("아바타 전달 파일")
                .font(.system(size: 15, weight: .bold, design: .rounded))
                .foregroundStyle(ReportTheme.text)

            VStack(alignment: .leading, spacing: 8) {
                Text("방송 종료 후 가로 전달용 영상과 bbox·영상 메타정보 JSON이 같이 생성되었습니다.")
                    .font(.system(size: 14, weight: .medium, design: .rounded))
                    .foregroundStyle(ReportTheme.text.opacity(0.72))
                    .lineSpacing(3)

                if let videoURL {
                    Text(videoURL.lastPathComponent)
                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                        .foregroundStyle(ReportTheme.primary)
                        .textSelection(.enabled)
                }

                if let schemaURL {
                    Text(schemaURL.lastPathComponent)
                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                        .foregroundStyle(ReportTheme.primary)
                        .textSelection(.enabled)
                }

                Button {
                    isShareSheetPresented = true
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 14, weight: .bold))

                        Text("전달 파일 공유하기")
                            .font(.system(size: 14, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .frame(height: 42)
                    .background(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(ReportTheme.primary)
                    )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(.white)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(ReportTheme.border, lineWidth: 1)
        )
    }

    func reportInfoCard(icon: String, title: String, value: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(ReportTheme.primary)
                .frame(width: 34, height: 34)
                .background(
                    Circle()
                        .fill(ReportTheme.primary.opacity(0.10))
                )

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.system(size: 12, weight: .semibold, design: .rounded))
                    .foregroundStyle(ReportTheme.text.opacity(0.54))

                Text(value)
                    .font(.system(size: 15, weight: .bold, design: .rounded))
                    .foregroundStyle(ReportTheme.text)
            }

            Spacer(minLength: 0)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(.white)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(ReportTheme.border, lineWidth: 1)
        )
    }

    func metricCard(title: String, value: String, subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 12, weight: .semibold, design: .rounded))
                .foregroundStyle(ReportTheme.text.opacity(0.54))

            Text(value)
                .font(.system(size: 24, weight: .bold, design: .rounded))
                .foregroundStyle(ReportTheme.text)

            Text(subtitle)
                .font(.system(size: 12, weight: .medium, design: .rounded))
                .foregroundStyle(ReportTheme.text.opacity(0.60))
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(.white)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(ReportTheme.border, lineWidth: 1)
        )
    }
}

struct PostStreamReportDetailView: View {
    @Environment(\.dismiss) private var dismiss

    let report: PostStreamAnalysisReport
    let onClose: () -> Void

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                detailHeader
                detailMetaCards
                detailSummaryCard
                detailMetricGrid
                detailViewerInsightSection
                detailHighlightSection
                detailContentRatioSection
                bottomCloseButton
            }
            .padding(.horizontal, 22)
            .padding(.top, 18)
            .padding(.bottom, 28)
        }
        .background(ReportTheme.canvas.ignoresSafeArea())
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar(.visible, for: .navigationBar)
        .toolbarBackground(ReportTheme.canvas, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbarColorScheme(.light, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(ReportTheme.text)
                }
            }
        }
    }

    var detailHeader: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(report.title)
                .font(.system(size: 28, weight: .bold, design: .rounded))
                .foregroundStyle(ReportTheme.text)

            Text("방송 회고와 다음 방송 준비에 활용할 수 있는 세부 분석 결과")
                .font(.system(size: 14, weight: .medium, design: .rounded))
                .foregroundStyle(ReportTheme.text.opacity(0.58))

            HStack(spacing: 10) {
                statusBadge(report.analysisStatus)

                Text(report.generatedAt.reportGeneratedLabel)
                    .font(.system(size: 12, weight: .semibold, design: .rounded))
                    .foregroundStyle(ReportTheme.text.opacity(0.50))
            }
        }
    }

    var detailMetaCards: some View {
        HStack(spacing: 12) {
            detailInfoCard(title: "방송 길이", value: report.durationSec.durationLabel)
            detailInfoCard(title: "Broadcast ID", value: report.broadcastID)
            detailInfoCard(title: "Analysis Job", value: report.analysisJobID)
        }
    }

    var detailSummaryCard: some View {
        VStack(alignment: .leading, spacing: 18) {
            detailSectionTitle("AI 요약")

            Text(report.summary)
                .font(.system(size: 15, weight: .medium, design: .rounded))
                .foregroundStyle(ReportTheme.text.opacity(0.80))
                .lineSpacing(4)

            Divider()
                .overlay(ReportTheme.border)

            detailBulletBlock("잘한 점", items: report.strengths, accent: ReportTheme.positive)
            detailBulletBlock("아쉬운 점", items: report.weaknesses, accent: ReportTheme.warning)
            detailBulletBlock("다음 방송 액션 아이템", items: report.actionItems, accent: ReportTheme.primary)
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(.white)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(ReportTheme.border, lineWidth: 1)
        )
    }

    var detailMetricGrid: some View {
        VStack(alignment: .leading, spacing: 14) {
            detailSectionTitle("핵심 지표")

            HStack(spacing: 12) {
                metricTile(title: "보호 처리 얼굴", value: "\(report.totalReplacedFaceCount)", icon: "shield.lefthalf.filled")
                metricTile(title: "최대 동시 인원", value: "\(report.maxSimultaneousCrowdCount)", icon: "person.3.fill")
            }

            HStack(spacing: 12) {
                metricTile(title: "하이라이트", value: "\(report.highlightCount)", icon: "sparkles.tv")
                metricTile(title: "최고 시청자", value: "\(report.peakViewerCount)", icon: "chart.line.uptrend.xyaxis")
            }
        }
    }

    var detailViewerInsightSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            detailSectionTitle("시청자 피크 인사이트")

            VStack(alignment: .leading, spacing: 12) {
                infoRow(title: "최고 시청자 수", value: "\(report.peakViewerCount)")
                infoRow(title: "발생 시점", value: report.peakOccurredAtLabel ?? "-")

                VStack(alignment: .leading, spacing: 8) {
                    Text("당시 장면 설명")
                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                        .foregroundStyle(ReportTheme.text.opacity(0.58))

                    Text(report.peakSceneDescription)
                        .font(.system(size: 15, weight: .medium, design: .rounded))
                        .foregroundStyle(ReportTheme.text.opacity(0.80))
                        .lineSpacing(4)
                }
            }
            .padding(18)
            .background(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(.white)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .stroke(ReportTheme.border, lineWidth: 1)
            )
        }
    }

    var detailHighlightSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            detailSectionTitle("하이라이트 포인트")

            if report.highlightMoments.isEmpty {
                emptyCard("현재 표시할 하이라이트 구간이 없습니다.")
            } else {
                VStack(spacing: 12) {
                    ForEach(report.highlightMoments) { moment in
                        HStack(alignment: .top, spacing: 14) {
                            VStack(spacing: 4) {
                                Text(moment.timeLabel)
                                    .font(.system(size: 13, weight: .bold, design: .rounded))
                                    .foregroundStyle(ReportTheme.primary)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 8)
                                    .background(
                                        Capsule()
                                            .fill(ReportTheme.primary.opacity(0.10))
                                    )
                            }
                            .frame(width: 84, alignment: .leading)

                            VStack(alignment: .leading, spacing: 6) {
                                Text(moment.title)
                                    .font(.system(size: 16, weight: .bold, design: .rounded))
                                    .foregroundStyle(ReportTheme.text)

                                Text(moment.description)
                                    .font(.system(size: 14, weight: .medium, design: .rounded))
                                    .foregroundStyle(ReportTheme.text.opacity(0.76))
                                    .lineSpacing(4)
                            }

                            Spacer(minLength: 0)
                        }
                        .padding(18)
                        .background(
                            RoundedRectangle(cornerRadius: 22, style: .continuous)
                                .fill(.white)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 22, style: .continuous)
                                .stroke(ReportTheme.border, lineWidth: 1)
                        )
                    }
                }
            }
        }
    }

    var detailContentRatioSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            detailSectionTitle("콘텐츠 비율 분석")

            VStack(spacing: 12) {
                ForEach(report.contentRatios) { ratio in
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text(ratio.contentType)
                                .font(.system(size: 15, weight: .bold, design: .rounded))
                                .foregroundStyle(ReportTheme.text)

                            Spacer()

                            Text("\(Int(ratio.percentage))% · \(ratio.durationSec.durationLabel)")
                                .font(.system(size: 13, weight: .semibold, design: .rounded))
                                .foregroundStyle(ReportTheme.text.opacity(0.60))
                        }

                        GeometryReader { geometry in
                            ZStack(alignment: .leading) {
                                Capsule()
                                    .fill(ReportTheme.surfaceMuted)

                                Capsule()
                                    .fill(
                                        LinearGradient(
                                            colors: [ReportTheme.primary, ReportTheme.primaryBright],
                                            startPoint: .leading,
                                            endPoint: .trailing
                                        )
                                    )
                                    .frame(width: geometry.size.width * max(min(ratio.percentage / 100.0, 1.0), 0.0))
                            }
                        }
                        .frame(height: 12)
                    }
                    .padding(18)
                    .background(
                        RoundedRectangle(cornerRadius: 22, style: .continuous)
                            .fill(.white)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 22, style: .continuous)
                            .stroke(ReportTheme.border, lineWidth: 1)
                    )
                }
            }
        }
    }

    var bottomCloseButton: some View {
        Button(action: onClose) {
            Text("닫기")
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 54)
                .background(
                    LinearGradient(
                        colors: [ReportTheme.primary, ReportTheme.primaryBright],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                .shadow(color: ReportTheme.primary.opacity(0.20), radius: 16, x: 0, y: 10)
        }
        .buttonStyle(.plain)
        .padding(.top, 6)
    }

    func detailInfoCard(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.system(size: 12, weight: .semibold, design: .rounded))
                .foregroundStyle(ReportTheme.text.opacity(0.54))

            Text(value)
                .font(.system(size: 14, weight: .bold, design: .rounded))
                .foregroundStyle(ReportTheme.text)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(.white)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(ReportTheme.border, lineWidth: 1)
        )
    }

    func metricTile(title: String, value: String, icon: String) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(ReportTheme.primary)
                .frame(width: 34, height: 34)
                .background(
                    Circle()
                        .fill(ReportTheme.primary.opacity(0.10))
                )

            Text(value)
                .font(.system(size: 24, weight: .bold, design: .rounded))
                .foregroundStyle(ReportTheme.text)

            Text(title)
                .font(.system(size: 13, weight: .semibold, design: .rounded))
                .foregroundStyle(ReportTheme.text.opacity(0.60))
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(.white)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(ReportTheme.border, lineWidth: 1)
        )
    }

    func infoRow(title: String, value: String) -> some View {
        HStack {
            Text(title)
                .font(.system(size: 13, weight: .semibold, design: .rounded))
                .foregroundStyle(ReportTheme.text.opacity(0.58))

            Spacer()

            Text(value)
                .font(.system(size: 15, weight: .bold, design: .rounded))
                .foregroundStyle(ReportTheme.text)
        }
    }

    func detailBulletBlock(_ title: String, items: [String], accent: Color) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.system(size: 14, weight: .bold, design: .rounded))
                .foregroundStyle(ReportTheme.text)

            ForEach(items, id: \.self) { item in
                HStack(alignment: .top, spacing: 10) {
                    Circle()
                        .fill(accent)
                        .frame(width: 8, height: 8)
                        .padding(.top, 6)

                    Text(item)
                        .font(.system(size: 14, weight: .medium, design: .rounded))
                        .foregroundStyle(ReportTheme.text.opacity(0.76))
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    func emptyCard(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 14, weight: .medium, design: .rounded))
            .foregroundStyle(ReportTheme.text.opacity(0.66))
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(.white)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .stroke(ReportTheme.border, lineWidth: 1)
            )
    }

    func detailSectionTitle(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 18, weight: .bold, design: .rounded))
            .foregroundStyle(ReportTheme.text)
    }
}

struct PostStreamReportArchiveView: View {
    let reports: [PostStreamAnalysisReport]
    let onClose: () -> Void

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                archiveHeader

                if reports.isEmpty {
                    emptyArchiveState
                } else {
                    VStack(spacing: 14) {
                        ForEach(reports) { report in
                            NavigationLink {
                                PostStreamReportDetailView(
                                    report: report,
                                    onClose: onClose
                                )
                            } label: {
                                archiveReportCard(report)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .padding(.horizontal, 22)
            .padding(.top, 18)
            .padding(.bottom, 28)
        }
        .background(ReportTheme.canvas.ignoresSafeArea())
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .navigationBar)
    }

    var archiveHeader: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 8) {
                Text("방송 회고록")
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .foregroundStyle(ReportTheme.text)

                Text("날짜별로 저장된 방송 리포트를 확인하고, 자세한 회고 화면으로 이어서 볼 수 있습니다.")
                    .font(.system(size: 14, weight: .medium, design: .rounded))
                    .foregroundStyle(ReportTheme.text.opacity(0.58))
                    .lineSpacing(4)
            }

            Spacer(minLength: 12)

            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(ReportTheme.text)
                    .frame(width: 34, height: 34)
                    .background(
                        Circle()
                            .fill(.white)
                    )
                    .overlay(
                        Circle()
                            .stroke(ReportTheme.border, lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)
        }
    }

    var emptyArchiveState: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("아직 저장된 방송 회고록이 없습니다.")
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundStyle(ReportTheme.text)

            Text("방송 종료 후 리포트가 생성되면 이 화면에서 날짜별로 확인할 수 있어요.")
                .font(.system(size: 14, weight: .medium, design: .rounded))
                .foregroundStyle(ReportTheme.text.opacity(0.66))
                .lineSpacing(4)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(.white)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(ReportTheme.border, lineWidth: 1)
        )
    }

    func archiveReportCard(_ report: PostStreamAnalysisReport) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(report.generatedAt.archiveDateLabel)
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .foregroundStyle(ReportTheme.primary)

                    Text(report.generatedAt.archiveDayLabel)
                        .font(.system(size: 20, weight: .bold, design: .rounded))
                        .foregroundStyle(ReportTheme.text)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .fill(ReportTheme.primary.opacity(0.08))
                )

                VStack(alignment: .leading, spacing: 8) {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(report.title)
                                .font(.system(size: 18, weight: .bold, design: .rounded))
                                .foregroundStyle(ReportTheme.text)

                            Text(report.summary)
                                .font(.system(size: 14, weight: .medium, design: .rounded))
                                .foregroundStyle(ReportTheme.text.opacity(0.72))
                                .lineLimit(3)
                                .multilineTextAlignment(.leading)
                        }

                        Spacer(minLength: 12)

                        statusBadge(report.analysisStatus)
                    }

                    HStack(spacing: 10) {
                        archiveMetaChip(icon: "clock.fill", text: report.durationSec.durationLabel)
                        archiveMetaChip(icon: "shield.lefthalf.filled", text: "보호 \(report.totalReplacedFaceCount)")
                        archiveMetaChip(icon: "sparkles.tv", text: "하이라이트 \(report.highlightCount)")
                    }
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(.white)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(ReportTheme.border, lineWidth: 1)
        )
        .shadow(color: ReportTheme.primary.opacity(0.06), radius: 14, x: 0, y: 8)
    }

    func archiveMetaChip(icon: String, text: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 11, weight: .bold))
            Text(text)
                .font(.system(size: 12, weight: .bold, design: .rounded))
        }
        .foregroundStyle(ReportTheme.text.opacity(0.72))
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(
            Capsule()
                .fill(ReportTheme.surfaceMuted)
        )
    }
}

private func statusBadge(_ status: PostStreamAnalysisStatus) -> some View {
    Text(status.title)
        .font(.system(size: 12, weight: .bold, design: .rounded))
        .foregroundStyle(status.badgeTextColor)
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(
            Capsule()
                .fill(status.badgeBackgroundColor)
        )
}

private enum ReportTheme {
    static let canvas = Color(red: 244.0 / 255.0, green: 249.0 / 255.0, blue: 252.0 / 255.0)
    static let text = Color(red: 16.0 / 255.0, green: 47.0 / 255.0, blue: 67.0 / 255.0)
    static let border = Color(red: 214.0 / 255.0, green: 232.0 / 255.0, blue: 242.0 / 255.0)
    static let surfaceMuted = Color(red: 234.0 / 255.0, green: 243.0 / 255.0, blue: 249.0 / 255.0)
    static let primary = Color(red: 3.0 / 255.0, green: 105.0 / 255.0, blue: 161.0 / 255.0)
    static let primaryBright = Color(red: 14.0 / 255.0, green: 165.0 / 255.0, blue: 233.0 / 255.0)
    static let positive = Color(red: 21.0 / 255.0, green: 128.0 / 255.0, blue: 61.0 / 255.0)
    static let warning = Color(red: 217.0 / 255.0, green: 119.0 / 255.0, blue: 6.0 / 255.0)
}

private extension PostStreamAnalysisStatus {
    var badgeBackgroundColor: Color {
        switch self {
        case .processing:
            return ReportTheme.primary.opacity(0.12)
        case .succeeded:
            return ReportTheme.positive.opacity(0.12)
        case .failed:
            return Color.red.opacity(0.12)
        }
    }

    var badgeTextColor: Color {
        switch self {
        case .processing:
            return ReportTheme.primary
        case .succeeded:
            return ReportTheme.positive
        case .failed:
            return .red
        }
    }
}

private extension Int {
    var durationLabel: String {
        let hours = self / 3600
        let minutes = (self % 3600) / 60
        let seconds = self % 60

        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, seconds)
        }

        return String(format: "%02d:%02d", minutes, seconds)
    }
}

private extension Date {
    var reportGeneratedLabel: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "yyyy.MM.dd HH:mm 생성"
        return formatter.string(from: self)
    }

    var archiveDateLabel: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "yyyy.MM.dd"
        return formatter.string(from: self)
    }

    var archiveDayLabel: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "EEEE"
        return formatter.string(from: self)
    }
}
