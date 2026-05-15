//
//  focusAppViewModel+sessionLifecycle.swift
//  focus
//
//  Created by Codex on 5/11/26.
//

import Foundation

extension FocusAppViewModel {
    func resolveSessionIDForStart() async -> String {
        guard let sessionAPIClient else {
            return UUID().uuidString
        }

        let fallbackSessionID = UUID().uuidString
        let width = max(Int(previewSourceSize.width.rounded()), 1)
        let height = max(Int(previewSourceSize.height.rounded()), 1)

        do {
            return try await sessionAPIClient.createSession(
                width: width,
                height: height
            )
        } catch {
            FocusLogger.warning(
                "원격 세션 생성에 실패해 로컬 세션 ID로 대체합니다. \(error.localizedDescription)",
                category: .network
            )
            return fallbackSessionID
        }
    }

    func closeRemoteSessionIfNeeded(sessionID: String) async {
        guard let sessionAPIClient else { return }

        do {
            try await sessionAPIClient.closeSession(sessionID: sessionID)
        } catch {
            FocusLogger.warning(
                "원격 세션 종료 요청 실패: \(error.localizedDescription)",
                category: .network
            )
        }
    }
}
