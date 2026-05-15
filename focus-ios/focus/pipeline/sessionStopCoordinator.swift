//
//  sessionStopCoordinator.swift
//  focus
//
//  Created by 이동언 on 3/7/26.
//

import Foundation

final class SessionStopCoordinator {
    private let lock = NSLock()
    private var isStopping = false

    func beginStopping() -> Bool {
        lock.lock()
        defer { lock.unlock() }

        guard !isStopping else { return false }
        isStopping = true
        return true
    }

    func reset() {
        lock.lock()
        isStopping = false
        lock.unlock()
    }

    func waitForAsyncWork(
        group: DispatchGroup,
        timeout: DispatchTime = .now() + 10
    ) -> Bool {
        group.wait(timeout: timeout) == .success
    }
}
