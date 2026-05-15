//
//  atomic.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation

final class Atomic<Value> {
    private let lock = NSLock()
    private var _value: Value

    init(_ value: Value) {
        self._value = value
    }

    var value: Value {
        lock.lock()
        defer { lock.unlock() }
        return _value
    }

    func set(_ newValue: Value) {
        lock.lock()
        _value = newValue
        lock.unlock()
    }

    @discardableResult
    func mutate<T>(_ transform: (inout Value) throws -> T) rethrows -> T {
        lock.lock()
        defer { lock.unlock() }
        return try transform(&_value)
    }

    @discardableResult
    func withValue<T>(_ work: (Value) throws -> T) rethrows -> T {
        lock.lock()
        let snapshot = _value
        lock.unlock()
        return try work(snapshot)
    }
}
