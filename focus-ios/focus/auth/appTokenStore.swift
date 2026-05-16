//
//  appTokenStore.swift
//  focus
//
//  Created by Codex on 5/13/26.
//

import Foundation

final class AppTokenStore {
    private enum Keys {
        static let accessToken = "focus.auth.accessToken"
        static let refreshToken = "focus.auth.refreshToken"
    }

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func save(accessToken: String, refreshToken: String) {
        defaults.set(accessToken, forKey: Keys.accessToken)
        defaults.set(refreshToken, forKey: Keys.refreshToken)
    }

    func getAccessToken() -> String? {
        defaults.string(forKey: Keys.accessToken)
    }

    func getRefreshToken() -> String? {
        defaults.string(forKey: Keys.refreshToken)
    }

    func clear() {
        defaults.removeObject(forKey: Keys.accessToken)
        defaults.removeObject(forKey: Keys.refreshToken)
    }
}
