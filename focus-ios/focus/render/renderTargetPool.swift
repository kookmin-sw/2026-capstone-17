//
//  renderTargetPool.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation
import CoreVideo

final class RenderTargetPool {
    private let lock = NSLock()
    private var cachedBuffers: [BufferKey: [CVPixelBuffer]] = [:]

    func makePixelBuffer(width: Int, height: Int) -> CVPixelBuffer? {
        let key = BufferKey(width: width, height: height)

        lock.lock()
        if var existing = cachedBuffers[key], !existing.isEmpty {
            let buffer = existing.removeLast()
            cachedBuffers[key] = existing
            lock.unlock()
            clear(buffer: buffer)
            return buffer
        }
        lock.unlock()

        return createPixelBuffer(width: width, height: height)
    }

    func recycle(_ buffer: CVPixelBuffer) {
        let key = BufferKey(
            width: CVPixelBufferGetWidth(buffer),
            height: CVPixelBufferGetHeight(buffer)
        )

        lock.lock()
        cachedBuffers[key, default: []].append(buffer)
        lock.unlock()
    }

    func removeAll() {
        lock.lock()
        cachedBuffers.removeAll()
        lock.unlock()
    }

    private func createPixelBuffer(width: Int, height: Int) -> CVPixelBuffer? {
        let attrs: [String: Any] = [
            kCVPixelBufferPixelFormatTypeKey as String: Int(kCVPixelFormatType_32BGRA),
            kCVPixelBufferWidthKey as String: width,
            kCVPixelBufferHeightKey as String: height,
            kCVPixelBufferCGImageCompatibilityKey as String: true,
            kCVPixelBufferCGBitmapContextCompatibilityKey as String: true,
            kCVPixelBufferMetalCompatibilityKey as String: true,
            kCVPixelBufferIOSurfacePropertiesKey as String: [:]
        ]

        var pixelBuffer: CVPixelBuffer?
        let status = CVPixelBufferCreate(
            kCFAllocatorDefault,
            width,
            height,
            kCVPixelFormatType_32BGRA,
            attrs as CFDictionary,
            &pixelBuffer
        )

        guard status == kCVReturnSuccess else { return nil }
        return pixelBuffer
    }

    private func clear(buffer: CVPixelBuffer) {
        CVPixelBufferLockBaseAddress(buffer, [])
        defer { CVPixelBufferUnlockBaseAddress(buffer, []) }

        if let baseAddress = CVPixelBufferGetBaseAddress(buffer) {
            let height = CVPixelBufferGetHeight(buffer)
            let bytesPerRow = CVPixelBufferGetBytesPerRow(buffer)
            memset(baseAddress, 0, height * bytesPerRow)
        }
    }
}

private struct BufferKey: Hashable {
    let width: Int
    let height: Int
}
