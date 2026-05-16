//
//  kakaoServerAuthClient.swift
//  focus
//
//  Created by Codex on 5/13/26.
//

import Foundation

struct KakaoServerLoginRequest: Encodable {
    let accessToken: String
}

struct APIEnvelope<T: Decodable>: Decodable {
    let success: Bool
    let message: String
    let data: T?
}

struct AppTokenResponseDTO: Decodable {
    let accessToken: String
    let refreshToken: String
}

final class KakaoServerAuthClient {
    private let baseURL: URL
    private let urlSession: URLSession
    private let jsonEncoder = JSONEncoder()
    private let jsonDecoder = JSONDecoder()

    init(baseURL: URL, urlSession: URLSession = .shared) {
        self.baseURL = baseURL
        self.urlSession = urlSession
    }

    func loginWithKakaoToken(_ kakaoAccessToken: String) async throws -> AppTokenResponseDTO {
        let url = baseURL
            .appendingPathComponent("api")
            .appendingPathComponent("auth")
            .appendingPathComponent("kakao")
            .appendingPathComponent("login")

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try jsonEncoder.encode(KakaoServerLoginRequest(accessToken: kakaoAccessToken))

        let (data, response) = try await urlSession.data(for: request)
        try validateHTTP(response: response, data: data)

        let decoded = try jsonDecoder.decode(APIEnvelope<AppTokenResponseDTO>.self, from: data)
        guard decoded.success, let tokenData = decoded.data else {
            throw NSError(
                domain: "KakaoServerAuthClient",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: decoded.message.isEmpty ? "서버 로그인에 실패했습니다." : decoded.message]
            )
        }

        return tokenData
    }

    private func validateHTTP(response: URLResponse, data: Data) throws {
        guard let http = response as? HTTPURLResponse else {
            throw NSError(
                domain: "KakaoServerAuthClient",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "HTTP 응답이 아닙니다."]
            )
        }

        guard (200...299).contains(http.statusCode) else {
            let message = String(data: data, encoding: .utf8) ?? "unknown"
            throw NSError(
                domain: "KakaoServerAuthClient",
                code: http.statusCode,
                userInfo: [NSLocalizedDescriptionKey: "HTTP \(http.statusCode): \(message)"]
            )
        }
    }
}
