//
//  kakaoLoginViewModel.swift
//  focus
//
//  Created by Codex on 5/13/26.
//

import Foundation
import UIKit

@MainActor
final class KakaoLoginViewModel: ObservableObject {
    @Published private(set) var isAuthenticated = false
    @Published private(set) var isChzzkConnected = false
    @Published private(set) var isBootstrapping = false
    @Published private(set) var isLoggingIn = false
    @Published private(set) var isCheckingChzzkStatus = false
    @Published private(set) var isOpeningChzzkConnect = false
    @Published var errorMessage: String?
    @Published private(set) var chzzkChannelName: String?
    @Published private(set) var chzzkWatchURL: URL?

    private let appTokenStore = AppTokenStore()
    private let serverAuthClient: KakaoServerAuthClient?
    private let accountAPIClient: AccountAPIClient?
    private var hasBootstrapped = false

    init() {
        if let baseURL = URL(string: FocusConstants.serverBaseURLString) {
            serverAuthClient = KakaoServerAuthClient(baseURL: baseURL)
            accountAPIClient = AccountAPIClient(baseURL: baseURL)
        } else {
            serverAuthClient = nil
            accountAPIClient = nil
        }

        KakaoSDKRuntime.initializeIfPossible(appKey: KakaoAuthConfiguration.nativeAppKey)
    }

    func bootstrapIfNeeded() {
        guard !hasBootstrapped else { return }
        hasBootstrapped = true

        Task {
            await bootstrap()
        }
    }

    func loginWithKakao() {
        Task {
            await login()
        }
    }

    func handleOpenURL(_ url: URL) {
        _ = KakaoSDKRuntime.handleOpenURL(url)
    }

    func refreshChzzkConnectionStatusIfNeeded() {
        guard isAuthenticated else { return }

        Task {
            await refreshChzzkConnectionStatus()
        }
    }

    func connectChzzk() {
        Task {
            await openChzzkConnectURL()
        }
    }

    private func bootstrap() async {
        isBootstrapping = true
        defer { isBootstrapping = false }
        errorMessage = nil

        guard KakaoAuthConfiguration.isConfigured else {
            errorMessage = "카카오 네이티브 앱 키를 설정하면 바로 로그인할 수 있어요."
            return
        }

        if FocusConstants.enableRemoteKakaoServerLogin {
            // For now, always require an explicit login tap so we can fetch a fresh
            // server token during the Kakao login flow instead of restoring stale state.
            appTokenStore.clear()
            isAuthenticated = false
            isChzzkConnected = false
            chzzkChannelName = nil
            chzzkWatchURL = nil
            return
        }

        let hasKakaoSession = await KakaoSDKRuntime.hasValidSession()
        isAuthenticated = hasKakaoSession
    }

    private func login() async {
        guard !isLoggingIn else { return }

        guard KakaoAuthConfiguration.isConfigured else {
            errorMessage = "카카오 네이티브 앱 키를 먼저 설정해주세요."
            return
        }

        if FocusConstants.enableRemoteKakaoServerLogin,
           FocusConstants.isPlaceholderServerBaseURL {
            errorMessage = "serverBaseURLString을 실제 서버 주소로 바꿔야 서버 로그인과 방송 송출을 사용할 수 있어요."
            return
        }

        isLoggingIn = true
        errorMessage = nil
        defer { isLoggingIn = false }

        do {
            let kakaoAccessToken = try await KakaoSDKRuntime.login()

            if FocusConstants.enableRemoteKakaoServerLogin,
               let serverAuthClient {
                let serverTokens = try await serverAuthClient.loginWithKakaoToken(kakaoAccessToken)
                appTokenStore.save(
                    accessToken: serverTokens.accessToken,
                    refreshToken: serverTokens.refreshToken
                )
            }

            isAuthenticated = true
            await refreshChzzkConnectionStatus()
        } catch {
            isAuthenticated = false
            isChzzkConnected = false
            errorMessage = error.localizedDescription
        }
    }

    private func refreshChzzkConnectionStatus() async {
        guard isAuthenticated else { return }
        guard let accessToken = appTokenStore.getAccessToken(), !accessToken.isEmpty else {
            isChzzkConnected = false
            chzzkChannelName = nil
            chzzkWatchURL = nil
            errorMessage = "서버 액세스 토큰이 없어 치지직 연동 상태를 확인할 수 없습니다. 다시 로그인해 주세요."
            return
        }
        guard let accountAPIClient else {
            isChzzkConnected = false
            errorMessage = "치지직 상태 확인 클라이언트를 초기화하지 못했습니다."
            return
        }

        isCheckingChzzkStatus = true
        defer { isCheckingChzzkStatus = false }

        do {
            let status = try await accountAPIClient.getChzzkConnectionStatus(accessToken: accessToken)
            isChzzkConnected = status.connected
            chzzkChannelName = status.channelName
            chzzkWatchURL = status.watchUrl.flatMap(URL.init(string:))
            if status.connected {
                errorMessage = nil
            }
        } catch {
            isChzzkConnected = false
            chzzkChannelName = nil
            chzzkWatchURL = nil
            errorMessage = "치지직 연동 상태 확인 실패\n\(error.localizedDescription)"
        }
    }

    private func openChzzkConnectURL() async {
        guard isAuthenticated else {
            errorMessage = "먼저 카카오 로그인을 완료해 주세요."
            return
        }
        guard let accessToken = appTokenStore.getAccessToken(), !accessToken.isEmpty else {
            errorMessage = "서버 액세스 토큰이 없어 치지직 연동을 시작할 수 없습니다. 다시 로그인해 주세요."
            return
        }
        guard let accountAPIClient else {
            errorMessage = "치지직 연동 클라이언트를 초기화하지 못했습니다."
            return
        }

        isOpeningChzzkConnect = true
        defer { isOpeningChzzkConnect = false }
        errorMessage = nil

        do {
            let authURL = try await accountAPIClient.getChzzkConnectURL(accessToken: accessToken)
            await MainActor.run {
                UIApplication.shared.open(authURL)
            }
            errorMessage = "치지직 연동을 완료한 뒤 앱으로 돌아오면 상태를 다시 확인할게요."
        } catch {
            errorMessage = "치지직 연동 URL 조회 실패\n\(error.localizedDescription)"
        }
    }
}
