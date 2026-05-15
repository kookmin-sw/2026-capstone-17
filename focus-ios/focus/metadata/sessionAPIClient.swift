//
//  sessionAPIClient.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation

final class SessionAPIClient {
    struct CreateSessionRequest: Encodable {
        let width: Int
        let height: Int
        let format: String
    }

    struct CreateSessionResponse: Decodable {
        let session_id: String
    }

    struct CloseSessionRequest: Encodable {
        let status: String
    }

    private let baseURL: URL
    private let urlSession: URLSession
    private let jsonEncoder = JSONEncoder()
    private let jsonDecoder = JSONDecoder()

    init(baseURL: URL, urlSession: URLSession = .shared) {
        self.baseURL = baseURL
        self.urlSession = urlSession
    }

    func createSession(
        width: Int,
        height: Int,
        format: String = "3dmm_raw_v1"
    ) async throws -> String {
        let url = baseURL.appendingPathComponent("api/sessions")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body = CreateSessionRequest(width: width, height: height, format: format)
        request.httpBody = try jsonEncoder.encode(body)

        let (data, response) = try await urlSession.data(for: request)
        try validateHTTP(response: response, data: data)

        let decoded = try jsonDecoder.decode(CreateSessionResponse.self, from: data)
        return decoded.session_id
    }

    func closeSession(sessionID: String, status: String = "ended") async throws {
        let url = baseURL
            .appendingPathComponent("api/sessions")
            .appendingPathComponent(sessionID)

        var request = URLRequest(url: url)
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body = CloseSessionRequest(status: status)
        request.httpBody = try jsonEncoder.encode(body)

        let (data, response) = try await urlSession.data(for: request)
        try validateHTTP(response: response, data: data)
    }

    private func validateHTTP(response: URLResponse, data: Data) throws {
        guard let http = response as? HTTPURLResponse else {
            throw NSError(domain: "SessionAPIClient", code: -1, userInfo: [
                NSLocalizedDescriptionKey: "HTTP 응답이 아닙니다."
            ])
        }

        guard (200...299).contains(http.statusCode) else {
            let message = String(data: data, encoding: .utf8) ?? "unknown"
            throw NSError(domain: "SessionAPIClient", code: http.statusCode, userInfo: [
                NSLocalizedDescriptionKey: "HTTP \(http.statusCode): \(message)"
            ])
        }
    }
}
