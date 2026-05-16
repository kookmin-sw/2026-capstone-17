//
//  accountAPIClient.swift
//  focus
//
//  Created by Codex on 5/16/26.
//

import Foundation

struct ChzzkConnectionStatusDTO: Decodable {
    let connected: Bool
    let channelId: String?
    let channelName: String?
    let watchUrl: String?
    let accessTokenExpiresAt: String?
    let connectedAt: String?
}

struct ChzzkConnectResponseDTO: Decodable {
    let authUrl: String
}

final class AccountAPIClient {
    private let baseURL: URL
    private let urlSession: URLSession
    private let jsonDecoder = JSONDecoder()

    init(baseURL: URL, urlSession: URLSession = .shared) {
        self.baseURL = baseURL
        self.urlSession = urlSession
    }

    func getChzzkConnectionStatus(accessToken: String) async throws -> ChzzkConnectionStatusDTO {
        let url = baseURL
            .appendingPathComponent("api")
            .appendingPathComponent("v1")
            .appendingPathComponent("platforms")
            .appendingPathComponent("chzzk")
            .appendingPathComponent("status")

        var request = try authorizedRequest(url: url, accessToken: accessToken)
        request.httpMethod = "GET"

        let (data, response) = try await urlSession.data(for: request)
        try validateHTTP(response: response, data: data)

        let envelope = try jsonDecoder.decode(APIEnvelope<ChzzkConnectionStatusDTO>.self, from: data)
        guard envelope.success, let status = envelope.data else {
            throw NSError(
                domain: "AccountAPIClient",
                code: -1,
                userInfo: [
                    NSLocalizedDescriptionKey: envelope.message.isEmpty
                        ? "치지직 연동 상태를 확인하지 못했습니다."
                        : envelope.message
                ]
            )
        }

        return status
    }

    func getChzzkConnectURL(accessToken: String) async throws -> URL {
        let url = baseURL
            .appendingPathComponent("api")
            .appendingPathComponent("v1")
            .appendingPathComponent("platforms")
            .appendingPathComponent("chzzk")
            .appendingPathComponent("connect")

        var request = try authorizedRequest(url: url, accessToken: accessToken)
        request.httpMethod = "GET"

        let (data, response) = try await urlSession.data(for: request)
        try validateHTTP(response: response, data: data)

        let envelope = try jsonDecoder.decode(APIEnvelope<ChzzkConnectResponseDTO>.self, from: data)
        guard envelope.success,
              let payload = envelope.data,
              let authURL = URL(string: payload.authUrl) else {
            throw NSError(
                domain: "AccountAPIClient",
                code: -4,
                userInfo: [NSLocalizedDescriptionKey: "치지직 연동 URL을 가져오지 못했습니다."]
            )
        }

        return authURL
    }

    private func authorizedRequest(url: URL, accessToken: String) throws -> URLRequest {
        guard !accessToken.isEmpty else {
            throw NSError(
                domain: "AccountAPIClient",
                code: -2,
                userInfo: [NSLocalizedDescriptionKey: "액세스 토큰이 비어 있습니다."]
            )
        }

        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        return request
    }

    private func validateHTTP(response: URLResponse, data: Data) throws {
        guard let http = response as? HTTPURLResponse else {
            throw NSError(
                domain: "AccountAPIClient",
                code: -3,
                userInfo: [NSLocalizedDescriptionKey: "HTTP 응답이 아닙니다."]
            )
        }

        guard (200...299).contains(http.statusCode) else {
            let message = parseServerErrorMessage(from: data)
            throw NSError(
                domain: "AccountAPIClient",
                code: http.statusCode,
                userInfo: [NSLocalizedDescriptionKey: "HTTP \(http.statusCode): \(message)"]
            )
        }
    }

    private func parseServerErrorMessage(from data: Data) -> String {
        guard !data.isEmpty else { return "unknown" }

        if let payload = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            var parts: [String] = []

            if let message = payload["message"] as? String, !message.isEmpty {
                parts.append(message)
            }
            if let error = payload["error"] as? String, !error.isEmpty, !parts.contains(error) {
                parts.append(error)
            }
            if let path = payload["path"] as? String, !path.isEmpty {
                parts.append("path: \(path)")
            }

            if !parts.isEmpty {
                return parts.joined(separator: " | ")
            }
        }

        return String(data: data, encoding: .utf8) ?? "unknown"
    }
}
