//
//  launchIntroView.swift
//  focus
//
//  Created by Codex on 4/1/26.
//

import SwiftUI

struct LaunchIntroView: View {
    let onTapContinue: () -> Void

    @State private var selectedSlide = 0

    private let timer = Timer.publish(every: 3.2, on: .main, in: .common).autoconnect()

    private let slides: [IntroSlide] = [
        IntroSlide(
            title: "스트리머는 그대로, 배경 인물은 안전하게",
            subtitle: "초상권 걱정 없는 스마트 라이브 스트리밍"
        ),
        IntroSlide(
            title: "방송중 스쳐가는 사람까지",
            subtitle: "자동으로 감지해 아바타로 전환해요"
        ),
        IntroSlide(
            title: "복잡한 설정 없이 바로 시작하는",
            subtitle: "안전한 라이브 스트리밍"
        )
    ]

    var body: some View {
        ZStack {
            backgroundLayer

            VStack(spacing: 0) {
                headerSection

                Spacer(minLength: 16)

                heroVisualSection

                Spacer(minLength: 28)

                copySection

                Spacer()

                footerSection
            }
            .padding(.horizontal, 24)
            .padding(.top, 24)
            .padding(.bottom, 34)
        }
        .ignoresSafeArea()
        .onReceive(timer) { _ in
            withAnimation(.easeInOut(duration: 0.28)) {
                selectedSlide = (selectedSlide + 1) % slides.count
            }
        }
    }
}

private extension LaunchIntroView {
    var backgroundLayer: some View {
        ZStack {
            LinearGradient(
                colors: [
                    LaunchTheme.background,
                    Color.white,
                    Color(red: 228.0 / 255.0, green: 243.0 / 255.0, blue: 250.0 / 255.0)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )

            Circle()
                .fill(LaunchTheme.primary.opacity(0.08))
                .frame(width: 280, height: 280)
                .blur(radius: 18)
                .offset(x: 160, y: -260)

            Circle()
                .fill(LaunchTheme.secondary.opacity(0.10))
                .frame(width: 220, height: 220)
                .blur(radius: 16)
                .offset(x: -120, y: 180)
        }
        .ignoresSafeArea()
    }

    var headerSection: some View {
        HStack {
            Text("FOCUS")
                .font(.system(size: 20, weight: .bold, design: .rounded))
                .foregroundStyle(LaunchTheme.primary.opacity(0.82))
                .tracking(1.4)

            Spacer()

            Text("Avatar 기본")
                .font(.system(size: 12, weight: .bold, design: .rounded))
                .foregroundStyle(LaunchTheme.primary)
                .padding(.horizontal, 11)
                .padding(.vertical, 7)
                .background(
                    Capsule()
                        .fill(Color.white.opacity(0.82))
                )
                .overlay(
                    Capsule()
                        .stroke(LaunchTheme.border, lineWidth: 1)
                )
        }
    }

    var heroVisualSection: some View {
        ZStack {
            Circle()
                .fill(Color.white.opacity(0.82))
                .frame(width: 334, height: 334)
                .overlay {
                    Circle()
                        .stroke(Color.white.opacity(0.9), lineWidth: 1)
                        .frame(width: 300, height: 300)
                }
                .overlay {
                    Circle()
                        .stroke(LaunchTheme.primary.opacity(0.10), lineWidth: 1)
                        .frame(width: 266, height: 266)
                }
                .shadow(color: LaunchTheme.primary.opacity(0.08), radius: 24, x: 0, y: 16)

            Image("focusIntroIllustration")
                .resizable()
                .interpolation(.high)
                .scaledToFit()
                .frame(width: 286, height: 286)
                .clipShape(RoundedRectangle(cornerRadius: 64, style: .continuous))
                .shadow(color: LaunchTheme.primary.opacity(0.18), radius: 22, x: 0, y: 16)
        }
        .frame(height: 348)
        .animation(.easeInOut(duration: 0.35), value: selectedSlide)
    }

    var copySection: some View {
        VStack(spacing: 18) {
            TabView(selection: $selectedSlide) {
                ForEach(Array(slides.enumerated()), id: \.offset) { index, slide in
                    VStack(spacing: 12) {
                        Text(slide.title)
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                            .foregroundStyle(LaunchTheme.text)
                            .multilineTextAlignment(.center)
                            .lineSpacing(4)
                            .frame(maxWidth: .infinity)

                        Text(slide.subtitle)
                            .font(.system(size: 16, weight: .medium, design: .rounded))
                            .foregroundStyle(LaunchTheme.text.opacity(0.68))
                            .multilineTextAlignment(.center)
                            .lineSpacing(4)
                            .frame(maxWidth: .infinity)
                    }
                    .padding(.horizontal, 12)
                    .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .frame(height: 132)

            HStack(spacing: 8) {
                ForEach(slides.indices, id: \.self) { index in
                    Capsule()
                        .fill(index == selectedSlide ? LaunchTheme.primary : LaunchTheme.border)
                        .frame(width: index == selectedSlide ? 28 : 8, height: 8)
                }
            }
        }
    }

    var footerSection: some View {
        Button(action: onTapContinue) {
            Text("시작하기")
                .font(.system(size: 18, weight: .bold, design: .rounded))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 58)
                .background(
                    LinearGradient(
                        colors: [LaunchTheme.cta, LaunchTheme.ctaBright],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                .shadow(color: LaunchTheme.cta.opacity(0.24), radius: 18, x: 0, y: 12)
        }
        .buttonStyle(.plain)
    }
}

private struct IntroSlide {
    let title: String
    let subtitle: String
}

private enum LaunchTheme {
    static let primary = Color(red: 3.0 / 255.0, green: 105.0 / 255.0, blue: 161.0 / 255.0)
    static let secondary = Color(red: 14.0 / 255.0, green: 165.0 / 255.0, blue: 233.0 / 255.0)
    static let cta = Color(red: 34.0 / 255.0, green: 197.0 / 255.0, blue: 94.0 / 255.0)
    static let ctaBright = Color(red: 61.0 / 255.0, green: 220.0 / 255.0, blue: 120.0 / 255.0)
    static let background = Color(red: 240.0 / 255.0, green: 249.0 / 255.0, blue: 255.0 / 255.0)
    static let text = Color(red: 12.0 / 255.0, green: 74.0 / 255.0, blue: 110.0 / 255.0)
    static let border = Color(red: 215.0 / 255.0, green: 234.0 / 255.0, blue: 245.0 / 255.0)
}

#Preview {
    LaunchIntroView(onTapContinue: {})
}
