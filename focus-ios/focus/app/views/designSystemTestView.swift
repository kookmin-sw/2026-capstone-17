//
//  designSystemTestView.swift
//  focus
//
//  Created by Codex on 4/1/26.
//

import SwiftUI
import AVFoundation

struct DesignSystemTestView: View {
    @StateObject private var viewModel = FocusAppViewModel()
    @State private var isMenuPresented = false

    var body: some View {
        ZStack {
            cameraBackgroundLayer
            cameraDimLayer
            cameraGuideLayer

            if isMenuPresented {
                menuBackdrop
            }
        }
        .overlay(alignment: .topLeading) {
            topStatusCluster
                .padding(.top, 18)
                .padding(.leading, 22)
                .allowsHitTesting(false)
        }
        .overlay(alignment: .bottom) {
            if let status = viewModel.transientStatusMessage {
                transientStatusChip(status)
                    .padding(.bottom, 24)
                    .allowsHitTesting(false)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .overlay(alignment: .topTrailing) {
            menuButton
                .padding(.top, 22)
                .padding(.trailing, 22)
        }
        .overlay(alignment: .bottomTrailing) {
            overlayContent
                .padding(.trailing, 24)
                .padding(.bottom, 26)
                .allowsHitTesting(!isMenuPresented)
        }
        .overlay(alignment: .trailing) {
            if isMenuPresented {
                menuPanel
                    .transition(.move(edge: .trailing).combined(with: .opacity))
            }
        }
        .ignoresSafeArea()
        .animation(.easeInOut(duration: 0.24), value: isMenuPresented)
        .task {
            await viewModel.prepareCameraIfNeeded()
        }
        .sheet(item: $viewModel.completedStreamReport) { report in
            NavigationStack {
                PostStreamReportSummarySheetView(
                    report: report,
                    avatarVideoURL: viewModel.lastAvatarVideoURL,
                    avatarSchemaURL: viewModel.lastAvatarSchemaURL,
                    onClose: { viewModel.dismissCompletedStreamReport() }
                )
            }
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $viewModel.isReportArchivePresented) {
            NavigationStack {
                PostStreamReportArchiveView(
                    reports: viewModel.archivedStreamReports,
                    onClose: { viewModel.dismissReportArchive() }
                )
            }
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
        }
        .alert("오류", isPresented: $viewModel.showErrorAlert) {
            Button("확인", role: .cancel) { }
        } message: {
            Text(viewModel.errorMessage ?? "알 수 없는 오류")
        }
    }
}

private extension DesignSystemTestView {
    var cameraBackgroundLayer: some View {
        GeometryReader { geometry in
            ZStack {
                Group {
                    if viewModel.isCameraReady {
                        PortraitLockedCameraPreview(session: viewModel.cameraSession)
                    } else {
                        LinearGradient(
                            colors: [
                                PreviewTheme.cameraFallbackTop,
                                PreviewTheme.cameraFallbackBottom
                            ],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    }
                }

                previewFaceOverlayLayer(previewSize: geometry.size)
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onEnded { value in
                        viewModel.handlePreviewTap(
                            at: value.location,
                            previewSize: geometry.size
                        )
                    }
            )
        }
        .ignoresSafeArea()
    }

    func previewFaceOverlayLayer(previewSize: CGSize) -> some View {
        let overlays = viewModel.previewFaceOverlays(for: previewSize)
        let debugOverlays = viewModel.previewDebugOverlays(for: previewSize)

        return ZStack {
            ForEach(debugOverlays) { overlay in
                debugPreviewOverlayView(overlay)
            }

            ForEach(overlays) { overlay in
                Rectangle()
                    .stroke(overlayStrokeColor(for: overlay.label), lineWidth: 5)
                    .frame(width: overlay.rect.width, height: overlay.rect.height)
                    .position(
                        x: overlay.rect.midX,
                        y: overlay.rect.midY
                    )
                    .shadow(color: overlayStrokeColor(for: overlay.label).opacity(0.34), radius: 10, x: 0, y: 0)
            }
        }
        .allowsHitTesting(false)
    }

    @ViewBuilder
    func debugPreviewOverlayView(_ overlay: PreviewDebugOverlay) -> some View {
        let tint = debugOverlayColor(for: overlay.kind)

        ZStack(alignment: .topLeading) {
            Rectangle()
                .strokeBorder(
                    tint,
                    style: StrokeStyle(
                        lineWidth: overlay.kind == .mask ? 2.5 : 2,
                        dash: overlay.kind == .tracker ? [] : [8, 6]
                    )
                )
                .frame(width: overlay.rect.width, height: overlay.rect.height)

            Text(overlay.title)
                .font(.system(size: 10, weight: .bold, design: .rounded))
                .foregroundStyle(.white)
                .padding(.horizontal, 6)
                .padding(.vertical, 4)
                .background(
                    Capsule(style: .continuous)
                        .fill(tint.opacity(0.92))
                )
                .offset(x: 4, y: -18)
        }
        .position(x: overlay.rect.midX, y: overlay.rect.midY)
    }

    var cameraDimLayer: some View {
        LinearGradient(
            colors: [
                Color.black.opacity(0.22),
                Color.black.opacity(0.10),
                Color.black.opacity(0.28)
            ],
            startPoint: .top,
            endPoint: .bottom
        )
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }

    var cameraGuideLayer: some View {
        EmptyView()
            .allowsHitTesting(false)
    }

    var topStatusCluster: some View {
        HStack(spacing: 8) {
            if viewModel.isRunning {
                liveStatusChip
            }

            previewChip(
                icon: "dot.radiowaves.left.and.right",
                title: viewModel.isCameraReady ? "Camera Ready" : "Camera Loading"
            )

            previewChip(
                icon: privacyIconName(for: viewModel.privacyMode),
                title: viewModel.privacyMode.title
            )

            if !viewModel.ownerProfiles.isEmpty {
                previewChip(
                    icon: "person.2.fill",
                    title: "Owner \(viewModel.ownerProfiles.count)"
                )
            }
        }
    }

    var overlayContent: some View {
        mainActionButton
    }

    var menuBackdrop: some View {
        Color.black.opacity(0.32)
            .ignoresSafeArea()
            .contentShape(Rectangle())
            .onTapGesture {
                isMenuPresented = false
            }
    }

    var menuButton: some View {
        Button {
            isMenuPresented.toggle()
        } label: {
            Image(systemName: "line.3.horizontal")
                .font(.system(size: 19, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: 44, height: 44)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(PreviewTheme.primary.opacity(0.78))
                        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(Color.white.opacity(0.18), lineWidth: 1)
                )
                .shadow(color: Color.black.opacity(0.18), radius: 14, x: 0, y: 8)
        }
        .buttonStyle(.plain)
    }

    var menuPanel: some View {
        GeometryReader { geometry in
            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 20) {
                    panelHeader

                    Divider()
                        .overlay(PreviewTheme.border)

                    ownerSection

                    cameraToggleSection

                    privacyToggleSection

                    menuActionSection
                }
                .padding(.top, 30)
                .padding(.horizontal, 24)
                .padding(.bottom, 32)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(width: min(396, max(328, geometry.size.width * 0.40)))
            .frame(maxHeight: .infinity, alignment: .topLeading)
            .background(
                RoundedRectangle(cornerRadius: 0)
                    .fill(Color.white.opacity(0.92))
                    .background(.ultraThinMaterial)
            )
            .overlay(alignment: .leading) {
                Rectangle()
                    .fill(PreviewTheme.border)
                    .frame(width: 1)
            }
            .shadow(color: Color.black.opacity(0.16), radius: 26, x: -10, y: 0)
            .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .ignoresSafeArea()
    }

    var panelHeader: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 12) {
                Text("Jisang Lee")
                    .font(.system(size: 26, weight: .bold, design: .rounded))
                    .foregroundStyle(PreviewTheme.text)
            }

            Spacer(minLength: 12)

            Button {
                isMenuPresented = false
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(PreviewTheme.text.opacity(0.88))
                    .frame(width: 34, height: 34)
                    .background(
                        Circle()
                            .fill(Color.white.opacity(0.92))
                    )
                    .overlay(
                        Circle()
                            .stroke(PreviewTheme.border, lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)
        }
    }

    var ownerSection: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 14) {
                Image(systemName: "person.crop.circle.fill")
                    .font(.system(size: 34))
                    .foregroundStyle(PreviewTheme.primary.opacity(0.74))

                VStack(alignment: .leading, spacing: 3) {
                    Text("Owner 관리")
                        .font(.system(size: 22, weight: .bold, design: .rounded))
                        .foregroundStyle(PreviewTheme.text)

                    Text("화면에 그대로 유지할 스트리머 프로필")
                        .font(.system(size: 13, weight: .medium, design: .rounded))
                        .foregroundStyle(PreviewTheme.text.opacity(0.62))
                }
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 16) {
                    ForEach(displayOwnerProfiles) { profile in
                        ownerCard(for: profile)
                    }
                }
                .padding(.trailing, 8)
            }
        }
    }

    func ownerCard(for profile: PreviewOwnerProfile) -> some View {
        let snapshotImage = ownerSnapshotImage(for: profile)

        return ZStack(alignment: .topTrailing) {
            Group {
                if let image = snapshotImage {
                    ownerSnapshotCardImage(image)
                } else {
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: profile.colors,
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                }
            }

            Button {
                viewModel.removeOwner(ownerID: profile.id)
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(8)
                    .background(
                        Circle()
                            .fill(Color.black.opacity(0.28))
                    )
            }
            .buttonStyle(.plain)
            .padding(8)
        }
        .frame(width: 120, height: 136)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(PreviewTheme.border, lineWidth: 1)
        )
    }

    func ownerSnapshotCardImage(_ image: Image) -> some View {
        return GeometryReader { geometry in
            image
                .resizable()
                .scaledToFill()
                .frame(width: geometry.size.height, height: geometry.size.width)
                .rotationEffect(.degrees(-90))
                .position(x: geometry.size.width / 2, y: geometry.size.height / 2)
        }
    }

    func ownerSnapshotImage(for profile: PreviewOwnerProfile) -> Image? {
        guard let snapshotURL = profile.snapshotURL else {
            return nil
        }

        if let uiImage = UIImage(contentsOfFile: snapshotURL.path) {
            return Image(uiImage: uiImage)
        }

        guard let data = try? Data(contentsOf: snapshotURL),
              let uiImage = UIImage(data: data) else {
            return nil
        }

        return Image(uiImage: uiImage)
    }

    var cameraToggleSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("카메라 전환")
                .font(.system(size: 15, weight: .semibold, design: .rounded))
                .foregroundStyle(PreviewTheme.text.opacity(0.68))

            PreviewSelectionBar(
                items: CameraFacing.allCases,
                selection: viewModel.cameraFacing,
                title: { $0.title },
                onSelect: { viewModel.switchCamera(to: $0) }
            )
        }
    }

    var privacyToggleSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("개인정보 처리")
                .font(.system(size: 15, weight: .semibold, design: .rounded))
                .foregroundStyle(PreviewTheme.text.opacity(0.68))

            PreviewSelectionBar(
                items: PrivacyMenuMode.allCases,
                selection: viewModel.privacyMode,
                title: { $0.title },
                onSelect: { viewModel.setPrivacyMode($0) }
            )
        }
    }

    var menuActionSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("추가 기능")
                .font(.system(size: 15, weight: .semibold, design: .rounded))
                .foregroundStyle(PreviewTheme.text.opacity(0.68))

            VStack(spacing: 10) {
                menuToggleButton(
                    icon: "viewfinder.rectangular",
                    title: "디버그 오버레이",
                    subtitle: viewModel.isDebugVisionOverlayEnabled ? "Detector / Tracker / Mask 표시 중" : "디버그 표시 끔",
                    accent: Color.orange,
                    isOn: viewModel.isDebugVisionOverlayEnabled
                ) {
                    viewModel.isDebugVisionOverlayEnabled.toggle()
                }

                menuWideActionButton(
                    icon: "book.pages.fill",
                    title: "방송 회고록 보기",
                    subtitle: "날짜별 방송 리포트 확인",
                    accent: PreviewTheme.primary
                ) {
                    isMenuPresented = false
                    viewModel.presentReportArchive()
                }

                menuWideActionButton(
                    icon: "film.stack.fill",
                    title: "원본클립 저장",
                    subtitle: "최근 1분 무모자이크 저장",
                    accent: PreviewTheme.stop
                ) {
                    viewModel.saveOriginalClipPlaceholder()
                }
            }
        }
    }

    func menuWideActionButton(
        icon: String,
        title: String,
        subtitle: String,
        accent: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(accent)
                    .frame(width: 40, height: 40)
                    .background(
                        Circle()
                            .fill(accent.opacity(0.10))
                    )

                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(PreviewTheme.text)

                    Text(subtitle)
                        .font(.system(size: 12, weight: .medium, design: .rounded))
                        .foregroundStyle(PreviewTheme.text.opacity(0.56))
                }

                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity)
            .frame(height: 72)
            .background(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(Color.white)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .stroke(PreviewTheme.border, lineWidth: 1)
            )
            .shadow(color: accent.opacity(0.08), radius: 12, x: 0, y: 6)
        }
        .buttonStyle(.plain)
    }

    func menuToggleButton(
        icon: String,
        title: String,
        subtitle: String,
        accent: Color,
        isOn: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(accent)
                    .frame(width: 40, height: 40)
                    .background(
                        Circle()
                            .fill(accent.opacity(0.10))
                    )

                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(PreviewTheme.text)

                    Text(subtitle)
                        .font(.system(size: 12, weight: .medium, design: .rounded))
                        .foregroundStyle(PreviewTheme.text.opacity(0.56))
                }

                Spacer(minLength: 0)

                Image(systemName: isOn ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(isOn ? accent : PreviewTheme.text.opacity(0.28))
            }
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity)
            .frame(height: 72)
            .background(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(Color.white)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .stroke(PreviewTheme.border, lineWidth: 1)
            )
            .shadow(color: accent.opacity(0.08), radius: 12, x: 0, y: 6)
        }
        .buttonStyle(.plain)
    }

    var mainActionButton: some View {
        Button {
            viewModel.toggleSession()
        } label: {
            Text(viewModel.isRunning ? "방송 종료하기" : "방송 시작하기")
                .font(.system(size: 20, weight: .bold, design: .rounded))
            .foregroundStyle(.white)
            .padding(.horizontal, 26)
            .frame(height: 60)
            .background(
                LinearGradient(
                    colors: [
                        viewModel.isRunning ? PreviewTheme.stop : PreviewTheme.cta,
                        viewModel.isRunning ? PreviewTheme.stopBright : PreviewTheme.ctaBright
                    ],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .clipShape(Capsule())
            .shadow(
                color: (viewModel.isRunning ? PreviewTheme.stop : PreviewTheme.cta).opacity(0.34),
                radius: 18,
                x: 0,
                y: 12
            )
        }
        .buttonStyle(.plain)
    }

    var liveStatusChip: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(.white)
                .frame(width: 8, height: 8)

            Text("LIVE")
                .font(.system(size: 12, weight: .bold, design: .rounded))
                .tracking(0.4)
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(
            Capsule()
                .fill(PreviewTheme.live)
        )
        .overlay(
            Capsule()
                .stroke(Color.white.opacity(0.14), lineWidth: 1)
        )
        .shadow(color: PreviewTheme.live.opacity(0.34), radius: 12, x: 0, y: 6)
    }

    func previewChip(icon: String, title: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 11, weight: .bold))

            Text(title)
                .font(.system(size: 12, weight: .semibold, design: .rounded))
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(
            Capsule()
                .fill(Color.black.opacity(0.22))
                .background(.ultraThinMaterial, in: Capsule())
        )
        .overlay(
            Capsule()
                .stroke(Color.white.opacity(0.10), lineWidth: 1)
        )
    }

    func transientStatusChip(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 13, weight: .semibold, design: .rounded))
            .foregroundStyle(.white)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(
                Capsule()
                    .fill(Color.black.opacity(0.34))
                    .background(.ultraThinMaterial, in: Capsule())
            )
            .overlay(
                Capsule()
                    .stroke(Color.white.opacity(0.12), lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.20), radius: 16, x: 0, y: 8)
    }

    func previewPanelChip(icon: String, title: String) -> some View {
        HStack(spacing: 7) {
            Image(systemName: icon)
                .font(.system(size: 11, weight: .bold))

            Text(title)
                .font(.system(size: 12, weight: .semibold, design: .rounded))
        }
        .foregroundStyle(PreviewTheme.primary)
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(
            Capsule()
                .fill(PreviewTheme.primary.opacity(0.10))
        )
    }

    func privacyIconName(for mode: PrivacyMenuMode) -> String {
        switch mode {
        case .avatar:
            return "sparkles.rectangle.stack.fill"
        case .mosaic:
            return "circle.grid.2x2.fill"
        case .disabled:
            return "eye.slash.fill"
        }
    }

    func overlayStrokeColor(for label: TrackLabel) -> Color {
        switch label {
        case .owner:
            return Color(red: 0.18, green: 0.84, blue: 0.42)
        case .other, .pending:
            return Color(red: 0.96, green: 0.24, blue: 0.22)
        }
    }

    func debugOverlayColor(for kind: PreviewDebugOverlayKind) -> Color {
        switch kind {
        case .detector:
            return .yellow
        case .tracker:
            return .cyan
        case .mask:
            return .pink
        }
    }

    var displayOwnerProfiles: [PreviewOwnerProfile] {
        viewModel.ownerProfiles.map(PreviewOwnerProfile.init(summary:))
    }
}

private struct PreviewOwnerProfile: Identifiable {
    let id: UUID
    let name: String
    let initials: String
    let colors: [Color]
    let snapshotURL: URL?

    init(summary: OwnerProfileSummary) {
        id = summary.id
        name = summary.displayName
        initials = Self.initials(from: summary.displayName)
        colors = Self.colors(for: summary.id)
        snapshotURL = summary.snapshotURL
    }

    private static func initials(from displayName: String) -> String {
        let tokens = displayName
            .split(whereSeparator: \.isWhitespace)
            .prefix(2)
            .compactMap { $0.first }

        let rawInitials = String(tokens)
        return rawInitials.isEmpty ? "OW" : rawInitials.uppercased()
    }

    private static func colors(for id: UUID) -> [Color] {
        let palettes: [[Color]] = [
            [PreviewTheme.primary, PreviewTheme.secondary],
            [Color(red: 0.13, green: 0.55, blue: 0.39), Color(red: 0.18, green: 0.76, blue: 0.42)],
            [Color(red: 0.18, green: 0.35, blue: 0.56), Color(red: 0.06, green: 0.65, blue: 0.91)],
            [Color(red: 0.71, green: 0.41, blue: 0.18), Color(red: 0.95, green: 0.67, blue: 0.24)],
            [Color(red: 0.62, green: 0.18, blue: 0.36), Color(red: 0.93, green: 0.38, blue: 0.52)]
        ]

        let index = abs(id.uuidString.hashValue) % palettes.count
        return palettes[index]
    }
}

private struct PreviewSelectionBar<Item: Identifiable & Hashable>: View {
    let items: [Item]
    let selection: Item
    let title: (Item) -> String
    let onSelect: (Item) -> Void

    var body: some View {
        HStack(spacing: 4) {
            ForEach(items) { item in
                Button {
                    onSelect(item)
                } label: {
                    Text(title(item))
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                        .foregroundStyle(PreviewTheme.text.opacity(0.90))
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background {
                            if item == selection {
                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .fill(Color.white)
                                    .shadow(color: PreviewTheme.primary.opacity(0.12), radius: 12, x: 0, y: 6)
                            }
                        }
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(PreviewTheme.surfaceMuted)
        )
    }
}

private enum PreviewTheme {
    static let primary = Color(red: 3.0 / 255.0, green: 105.0 / 255.0, blue: 161.0 / 255.0)
    static let secondary = Color(red: 14.0 / 255.0, green: 165.0 / 255.0, blue: 233.0 / 255.0)
    static let cta = Color(red: 34.0 / 255.0, green: 197.0 / 255.0, blue: 94.0 / 255.0)
    static let ctaBright = Color(red: 61.0 / 255.0, green: 220.0 / 255.0, blue: 120.0 / 255.0)
    static let stop = Color(red: 214.0 / 255.0, green: 48.0 / 255.0, blue: 49.0 / 255.0)
    static let stopBright = Color(red: 239.0 / 255.0, green: 82.0 / 255.0, blue: 84.0 / 255.0)
    static let live = Color(red: 255.0 / 255.0, green: 78.0 / 255.0, blue: 79.0 / 255.0)
    static let text = Color(red: 12.0 / 255.0, green: 74.0 / 255.0, blue: 110.0 / 255.0)
    static let border = Color(red: 215.0 / 255.0, green: 234.0 / 255.0, blue: 245.0 / 255.0)
    static let surfaceMuted = Color(red: 243.0 / 255.0, green: 249.0 / 255.0, blue: 253.0 / 255.0)
    static let cameraFallbackTop = Color(red: 15.0 / 255.0, green: 42.0 / 255.0, blue: 58.0 / 255.0)
    static let cameraFallbackBottom = Color(red: 6.0 / 255.0, green: 20.0 / 255.0, blue: 29.0 / 255.0)
}

struct DesignSystemTestView_Previews: PreviewProvider {
    static var previews: some View {
        DesignSystemTestView()
            .previewInterfaceOrientation(.landscapeRight)
    }
}
