//
//  kakaoSDKRuntime.swift
//  focus
//
//  Created by Codex on 5/13/26.
//

import Foundation

#if canImport(KakaoSDKCommon) && canImport(KakaoSDKAuth) && canImport(KakaoSDKUser)
import KakaoSDKCommon
import KakaoSDKAuth
import KakaoSDKUser

enum KakaoSDKRuntime {
    static var isAvailable: Bool { true }

    static func initializeIfPossible(appKey: String) {
        guard !appKey.isEmpty else { return }
        KakaoSDK.initSDK(appKey: appKey)
    }

    static func handleOpenURL(_ url: URL) -> Bool {
        guard AuthApi.isKakaoTalkLoginUrl(url) else { return false }
        return AuthController.handleOpenUrl(url: url)
    }

    static func hasValidSession() async -> Bool {
        guard AuthApi.hasToken() else { return false }

        return await withCheckedContinuation { continuation in
            UserApi.shared.accessTokenInfo { info, error in
                continuation.resume(returning: info != nil && error == nil)
            }
        }
    }

    static func currentAccessToken() -> String? {
        TokenManager.manager.getToken()?.accessToken
    }

    @MainActor
    static func login() async throws -> String {
        try await withCheckedThrowingContinuation { continuation in
            let callback: (OAuthToken?, Error?) -> Void = { token, error in
                if let error {
                    continuation.resume(throwing: error)
                    return
                }

                guard let accessToken = token?.accessToken, !accessToken.isEmpty else {
                    continuation.resume(
                        throwing: NSError(
                            domain: "KakaoSDKRuntime",
                            code: -1,
                            userInfo: [NSLocalizedDescriptionKey: "카카오 액세스 토큰을 받지 못했습니다."]
                        )
                    )
                    return
                }

                continuation.resume(returning: accessToken)
            }

            if UserApi.isKakaoTalkLoginAvailable() {
                UserApi.shared.loginWithKakaoTalk(
                    launchMethod: .CustomScheme,
                    completion: callback
                )
            } else {
                UserApi.shared.loginWithKakaoAccount(completion: callback)
            }
        }
    }
}
#else
enum KakaoSDKRuntime {
    static var isAvailable: Bool { false }

    static func initializeIfPossible(appKey: String) {}

    static func handleOpenURL(_ url: URL) -> Bool {
        false
    }

    static func hasValidSession() async -> Bool {
        false
    }

    static func currentAccessToken() -> String? {
        nil
    }

    static func login() async throws -> String {
        throw NSError(
            domain: "KakaoSDKRuntime",
            code: -1,
            userInfo: [NSLocalizedDescriptionKey: "카카오 SDK가 아직 프로젝트에 설치되지 않았습니다."]
        )
    }
}
#endif
