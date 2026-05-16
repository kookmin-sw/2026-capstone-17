//
//  kakaoAuthConfiguration.swift
//  focus
//
//  Created by Codex on 5/13/26.
//

import Foundation

enum KakaoAuthConfiguration {
    static var nativeAppKey: String {
        (Bundle.main.object(forInfoDictionaryKey: "KakaoNativeAppKey") as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }

    static var isConfigured: Bool {
        !nativeAppKey.isEmpty
    }
}
