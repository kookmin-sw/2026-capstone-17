//
//  broadcastAPIClient.swift
//  focus
//
//  Created by Codex on 5/16/26.
//

import Foundation

final class BroadcastAPIClient {
    private let baseURL: URL
    private let urlSession: URLSession
    private let jsonEncoder = JSONEncoder()
    private let jsonDecoder = JSONDecoder()

    init(baseURL: URL, urlSession: URLSession = .shared) {
        self.baseURL = baseURL
        self.urlSession = urlSession
    }

    func createBroadcast(
        title: String,
        accessToken: String
    ) async throws -> BroadcastSession {
        let url = baseURL
            .appendingPathComponent("api")
            .appendingPathComponent("v1")
            .appendingPathComponent("broadcasts")

        var request = try authorizedRequest(url: url, accessToken: accessToken)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try jsonEncoder.encode(CreateBroadcastRequestDTO(title: title))

        return try await executeBroadcastRequest(request)
    }

    func startBroadcast(
        broadcastID: String,
        avatarID: String?,
        accessToken: String
    ) async throws -> BroadcastSession {
        let url = baseURL
            .appendingPathComponent("api")
            .appendingPathComponent("v1")
            .appendingPathComponent("broadcasts")
            .appendingPathComponent(broadcastID)
            .appendingPathComponent("start")

        var request = try authorizedRequest(url: url, accessToken: accessToken)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try jsonEncoder.encode(StartBroadcastRequestDTO(avatarId: avatarID))

        return try await executeBroadcastRequest(request)
    }

    func stopBroadcast(
        broadcastID: String,
        accessToken: String
    ) async throws -> BroadcastSession {
        let url = baseURL
            .appendingPathComponent("api")
            .appendingPathComponent("v1")
            .appendingPathComponent("broadcasts")
            .appendingPathComponent(broadcastID)
            .appendingPathComponent("stop")

        var request = try authorizedRequest(url: url, accessToken: accessToken)
        request.httpMethod = "POST"
        return try await executeBroadcastRequest(request)
    }

    func streamerHeartbeat(
        broadcastID: String,
        accessToken: String
    ) async throws {
        let url = baseURL
            .appendingPathComponent("api")
            .appendingPathComponent("v1")
            .appendingPathComponent("broadcasts")
            .appendingPathComponent(broadcastID)
            .appendingPathComponent("streamer-heartbeat")

        var request = try authorizedRequest(url: url, accessToken: accessToken)
        request.httpMethod = "POST"

        let (data, response) = try await urlSession.data(for: request)
        try validateHTTP(response: response, data: data)
    }

    private func executeBroadcastRequest(_ request: URLRequest) async throws -> BroadcastSession {
        let (data, response) = try await urlSession.data(for: request)
        try validateHTTP(response: response, data: data)

        let envelope = try jsonDecoder.decode(APIEnvelope<BroadcastResponseDTO>.self, from: data)
        guard envelope.success, let dto = envelope.data else {
            throw NSError(
                domain: "BroadcastAPIClient",
                code: -1,
                userInfo: [
                    NSLocalizedDescriptionKey: envelope.message.isEmpty
                        ? "방송 요청에 실패했습니다."
                        : envelope.message
                ]
            )
        }
        return dto.toDomain()
    }

    private func authorizedRequest(
        url: URL,
        accessToken: String
    ) throws -> URLRequest {
        guard !accessToken.isEmpty else {
            throw NSError(
                domain: "BroadcastAPIClient",
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
                domain: "BroadcastAPIClient",
                code: -3,
                userInfo: [NSLocalizedDescriptionKey: "HTTP 응답이 아닙니다."]
            )
        }

        guard (200...299).contains(http.statusCode) else {
            let message = parseServerErrorMessage(from: data)
            throw NSError(
                domain: "BroadcastAPIClient",
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
