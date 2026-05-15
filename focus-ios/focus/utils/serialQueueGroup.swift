//
//  serialQueueGroup.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation

final class SerialQueueGroup {
    private let group = DispatchGroup()
    private let lock = NSLock()
    private var queues: [String: DispatchQueue] = [:]

    func queue(named name: String, qos: DispatchQoS = .utility) -> DispatchQueue {
        lock.lock()
        defer { lock.unlock() }

        if let existing = queues[name] {
            return existing
        }

        let queue = DispatchQueue(label: name, qos: qos)
        queues[name] = queue
        return queue
    }

    func async(
        on queue: DispatchQueue,
        execute work: @escaping () -> Void
    ) {
        group.enter()
        queue.async {
            work()
            self.group.leave()
        }
    }

    func async(
        onNamedQueue name: String,
        qos: DispatchQoS = .utility,
        execute work: @escaping () -> Void
    ) {
        let queue = queue(named: name, qos: qos)
        async(on: queue, execute: work)
    }

    @discardableResult
    func wait(timeout: DispatchTime = .now() + 10) -> Bool {
        group.wait(timeout: timeout) == .success
    }

    func notify(
        queue: DispatchQueue = .main,
        execute work: @escaping () -> Void
    ) {
        group.notify(queue: queue, execute: work)
    }
}
