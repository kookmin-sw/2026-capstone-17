//
//  chzzkConnectGateView.swift
//  focus
//
//  Created by Codex on 5/16/26.
//

import SwiftUI

struct ChzzkConnectGateView: View {
    let onTapConnect: () -> Void
    let onTapRefresh: () -> Void
    let isLoading: Bool
    let channelName: String?
    let watchURL: URL?
    let errorMessage: String?

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [
                    Color(red: 236.0 / 255.0, green: 249.0 / 255.0, blue: 255.0 / 255.0),
                    Color.white,
                    Color(red: 244.0 / 255.0, green: 247.0 / 255.0, blue: 255.0 / 255.0)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer(minLength: 36)

                VStack(spacing: 18) {
                    Image(systemName: "dot.radiowaves.left.and.right")
                        .font(.system(size: 34, weight: .bold))
                        .foregroundStyle(ConnectTheme.primary)
                        .frame(width: 84, height: 84)
                        .background(
                            Circle()
                                .fill(Color.white.opacity(0.92))
                                .shadow(color: ConnectTheme.primary.opacity(0.12), radius: 18, x: 0, y: 12)
                        )

                    Text("치지직 연동이 필요해요")
                        .font(.system(size: 30, weight: .bold, design: .rounded))
                        .foregroundStyle(ConnectTheme.text)
                        .multilineTextAlignment(.center)

                    Text("카카오 로그인은 완료됐고, 이제 치지직 채널만 연결하면 바로 방송을 시작할 수 있어요.")
                        .font(.system(size: 16, weight: .medium, design: .rounded))
                        .foregroundStyle(ConnectTheme.text.opacity(0.7))
                        .multilineTextAlignment(.center)
                        .lineSpacing(4)
                        .padding(.horizontal, 12)
                }

                Spacer(minLength: 28)

                VStack(alignment: .leading, spacing: 14) {
                    statusRow(
                        icon: "checkmark.seal.fill",
                        title: "카카오 로그인",
                        subtitle: "완료"
                    )

                    statusRow(
                        icon: "dot.radiowaves.left.and.right",
                        title: "치지직 채널 연동",
                        subtitle: channelName ?? "아직 연결되지 않음"
                    )

                    if let watchURL {
                        statusRow(
                            icon: "link",
                            title: "연결 채널",
                            subtitle: watchURL.absoluteString
                        )
                    }
                }
                .padding(20)
                .background(
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .fill(Color.white.opacity(0.9))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .stroke(Color.white.opacity(0.9), lineWidth: 1)
                )

                Spacer()

                VStack(spacing: 12) {
                    Button(action: onTapConnect) {
                        HStack(spacing: 10) {
                            if isLoading {
                                ProgressView()
                                    .tint(.white)
                            } else {
                                Image(systemName: "safari.fill")
                                    .font(.system(size: 18, weight: .bold))
                            }

                            Text(isLoading ? "연동 페이지 준비 중..." : "치지직 연동하기")
                                .font(.system(size: 18, weight: .bold, design: .rounded))
                        }
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 58)
                        .background(ConnectTheme.primary)
                        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                        .shadow(color: ConnectTheme.primary.opacity(0.24), radius: 16, x: 0, y: 10)
                    }
                    .buttonStyle(.plain)
                    .disabled(isLoading)

                    Button(action: onTapRefresh) {
                        Text("연동 완료 후 상태 다시 확인")
                            .font(.system(size: 15, weight: .semibold, design: .rounded))
                            .foregroundStyle(ConnectTheme.primary)
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                            .background(Color.white.opacity(0.92))
                            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: 18, style: .continuous)
                                    .stroke(ConnectTheme.primary.opacity(0.14), lineWidth: 1)
                            )
                    }
                    .buttonStyle(.plain)
                    .disabled(isLoading)

                    if let errorMessage, !errorMessage.isEmpty {
                        Text(errorMessage)
                            .font(.system(size: 13, weight: .medium, design: .rounded))
                            .foregroundStyle(Color.red.opacity(0.82))
                            .multilineTextAlignment(.center)
                            .lineSpacing(3)
                            .padding(.horizontal, 8)
                    }
                }
                .padding(.bottom, 34)
            }
            .padding(.horizontal, 24)
        }
    }
}

private extension ChzzkConnectGateView {
    func statusRow(icon: String, title: String, subtitle: String) -> some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(ConnectTheme.primary)
                .frame(width: 36, height: 36)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(ConnectTheme.primary.opacity(0.08))
                )

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.system(size: 15, weight: .bold, design: .rounded))
                    .foregroundStyle(ConnectTheme.text)

                Text(subtitle)
                    .font(.system(size: 13, weight: .medium, design: .rounded))
                    .foregroundStyle(ConnectTheme.text.opacity(0.68))
                    .lineLimit(2)
            }

            Spacer()
        }
    }
}

private enum ConnectTheme {
    static let primary = Color(red: 4.0 / 255.0, green: 120.0 / 255.0, blue: 87.0 / 255.0)
    static let text = Color(red: 18.0 / 255.0, green: 56.0 / 255.0, blue: 84.0 / 255.0)
}

#Preview {
    ChzzkConnectGateView(
        onTapConnect: {},
        onTapRefresh: {},
        isLoading: false,
        channelName: nil,
        watchURL: nil,
        errorMessage: nil
    )
}
