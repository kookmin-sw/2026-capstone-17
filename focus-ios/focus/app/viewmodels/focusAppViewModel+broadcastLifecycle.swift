//
//  focusAppViewModel+broadcastLifecycle.swift
//  focus
//
//  Created by Codex on 5/16/26.
//

import Foundation

extension FocusAppViewModel {
    func prepareRemoteBroadcastIfNeeded() async throws -> SRTBroadcastStreamer? {
        guard let broadcastAPIClient else {
            return nil
        }
        guard let accountAPIClient else {
            throw NSError(
                domain: "FocusBroadcast",
                code: -11,
                userInfo: [NSLocalizedDescriptionKey: "계정 상태 확인 클라이언트를 초기화하지 못했습니다."]
            )
        }

        guard !FocusConstants.isPlaceholderServerBaseURL else {
            throw NSError(
                domain: "FocusBroadcast",
                code: -10,
                userInfo: [NSLocalizedDescriptionKey: "serverBaseURLString을 실제 서버 주소로 바꿔야 치지직 방송 송출을 시작할 수 있습니다."]
            )
        }

        guard let accessToken = appTokenStore.getAccessToken(), !accessToken.isEmpty else {
            throw NSError(
                domain: "FocusBroadcast",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "서버 액세스 토큰이 없습니다. 카카오 서버 로그인을 먼저 완료해주세요."]
            )
        }

        let chzzkStatus = try await accountAPIClient.getChzzkConnectionStatus(accessToken: accessToken)
        guard chzzkStatus.connected else {
            throw NSError(
                domain: "FocusBroadcast",
                code: -12,
                userInfo: [
                    NSLocalizedDescriptionKey: "치지직 채널 연동이 필요합니다. 먼저 치지직 계정 연동 상태를 확인해 주세요."
                ]
            )
        }

        let createdBroadcast = try await broadcastAPIClient.createBroadcast(
            title: FocusConstants.defaultBroadcastTitle,
            accessToken: accessToken
        )
        guard !createdBroadcast.streamKey.isEmpty else {
            throw NSError(
                domain: "FocusBroadcast",
                code: -13,
                userInfo: [NSLocalizedDescriptionKey: "방송 생성은 되었지만 streamKey가 비어 있습니다."]
            )
        }

        let streamer = SRTBroadcastStreamer()
        try await streamer.start(
            host: FocusConstants.mediaMtxHost,
            port: FocusConstants.mediaMtxPort,
            streamKey: createdBroadcast.streamKey
        )

        preparedBroadcastSession = PreparedBroadcastSession(
            broadcast: createdBroadcast,
            accessToken: accessToken
        )
        preparedBroadcastStartTask?.cancel()
        preparedBroadcastStartTask = nil
        activeBroadcastStreamer = streamer
        activeBroadcastID = createdBroadcast.broadcastID
        activeBroadcastOutputMode = createdBroadcast.outputMode == "UNKNOWN"
            ? nil
            : createdBroadcast.outputMode
        activeBroadcastWatchURLText =
            createdBroadcast.watchURL?.absoluteString ?? createdBroadcast.hlsURL?.absoluteString
        activeBroadcastStartFailureReason = createdBroadcast.lastStartFailureReason
        return streamer
    }

    func confirmPreparedRemoteBroadcastStartIfNeeded() {
        guard broadcastAPIClient != nil,
              activeBroadcastSession == nil,
              preparedBroadcastStartTask == nil,
              preparedBroadcastSession != nil else {
            return
        }

        preparedBroadcastStartTask = Task { @MainActor [weak self] in
            guard let self else { return }

            do {
                try await Task.sleep(
                    nanoseconds: FocusConstants.remoteBroadcastStartDelayMs * 1_000_000
                )
            } catch {
                self.preparedBroadcastStartTask = nil
                return
            }

            guard !Task.isCancelled,
                  let broadcastAPIClient = self.broadcastAPIClient,
                  self.activeBroadcastSession == nil,
                  let prepared = self.preparedBroadcastSession else {
                self.preparedBroadcastStartTask = nil
                return
            }

            do {
                let startedBroadcast = try await broadcastAPIClient.startBroadcast(
                    broadcastID: prepared.broadcast.broadcastID,
                    avatarID: resolvedBroadcastAvatarID(),
                    accessToken: prepared.accessToken
                )

                self.activeBroadcastSession = startedBroadcast
                self.activeBroadcastID = startedBroadcast.broadcastID
                self.activeBroadcastOutputMode = startedBroadcast.outputMode
                self.activeBroadcastWatchURLText =
                    startedBroadcast.watchURL?.absoluteString ?? startedBroadcast.hlsURL?.absoluteString
                self.activeBroadcastStartFailureReason = startedBroadcast.lastStartFailureReason
                self.startBroadcastHeartbeat(
                    accessToken: prepared.accessToken,
                    broadcastID: startedBroadcast.broadcastID
                )
                self.preparedBroadcastSession = nil
                self.preparedBroadcastStartTask = nil

                if startedBroadcast.outputMode != "CHZZK_RTMP" {
                    let reason = startedBroadcast.lastStartFailureReason?.trimmingCharacters(in: .whitespacesAndNewlines)
                    let suffix = reason.flatMap { $0.isEmpty ? nil : $0 } ?? "서버가 치지직 대신 HLS fallback으로 전환한 것 같습니다."
                    self.handleError("치지직 송출로 시작되지 않았습니다. 현재 출력 모드: \(startedBroadcast.outputMode). \(suffix)")
                }
            } catch {
                self.preparedBroadcastStartTask = nil
                self.preparedBroadcastSession = nil
                self.handleError(error.localizedDescription)
                self.stopSession()
            }
        }
    }

    private func resolvedBroadcastAvatarID() -> String? {
        switch privacyMode {
        case .avatar:
            return FocusConstants.defaultBroadcastAvatarID
        case .mosaic, .disabled:
            return nil
        }
    }

    func stopRemoteBroadcastIfNeeded() async {
        preparedBroadcastStartTask?.cancel()
        preparedBroadcastStartTask = nil
        broadcastHeartbeatTask?.cancel()
        broadcastHeartbeatTask = nil

        if let activeBroadcastStreamer {
            await activeBroadcastStreamer.stop()
        }

        if let broadcastAPIClient,
           let accessToken = appTokenStore.getAccessToken(),
           let broadcastID = activeBroadcastSession?.broadcastID {
            do {
                _ = try await broadcastAPIClient.stopBroadcast(
                    broadcastID: broadcastID,
                    accessToken: accessToken
                )
            } catch {
                FocusLogger.warning(
                    "원격 방송 종료 요청 실패: \(error.localizedDescription)",
                    category: .network
                )
            }
        }

        activeBroadcastStreamer = nil
        activeBroadcastSession = nil
        preparedBroadcastSession = nil
        activeBroadcastID = nil
        activeBroadcastOutputMode = nil
        activeBroadcastWatchURLText = nil
        activeBroadcastStartFailureReason = nil
    }

    private func startBroadcastHeartbeat(accessToken: String, broadcastID: String) {
        broadcastHeartbeatTask?.cancel()
        broadcastHeartbeatTask = Task { [weak self] in
            guard let self else { return }

            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 10_000_000_000)
                guard !Task.isCancelled,
                      let broadcastAPIClient = self.broadcastAPIClient else {
                    continue
                }

                do {
                    try await broadcastAPIClient.streamerHeartbeat(
                        broadcastID: broadcastID,
                        accessToken: accessToken
                    )
                } catch {
                    FocusLogger.warning(
                        "streamer heartbeat 실패: \(error.localizedDescription)",
                        category: .network
                    )
                }
            }
        }
    }
}
