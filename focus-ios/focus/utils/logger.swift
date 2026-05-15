//
//  logger.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation

enum FocusLogCategory: String {
    case app = "APP"
    case capture = "CAPTURE"
    case pipeline = "PIPELINE"
    case inference = "INFERENCE"
    case tracking = "TRACKING"
    case render = "RENDER"
    case metadata = "METADATA"
    case streaming = "STREAMING"
    case network = "NETWORK"
    case ui = "UI"
}

enum FocusLogger {
    static var isEnabled: Bool = true

    static func debug(_ message: @autoclosure () -> String,
                      category: FocusLogCategory = .app,
                      file: String = #fileID,
                      function: String = #function,
                      line: Int = #line) {
#if DEBUG
        guard isEnabled else { return }
        print(format(level: "DEBUG",
                     message: message(),
                     category: category,
                     file: file,
                     function: function,
                     line: line))
#endif
    }

    static func info(_ message: @autoclosure () -> String,
                     category: FocusLogCategory = .app,
                     file: String = #fileID,
                     function: String = #function,
                     line: Int = #line) {
        guard isEnabled else { return }
        print(format(level: "INFO",
                     message: message(),
                     category: category,
                     file: file,
                     function: function,
                     line: line))
    }

    static func warning(_ message: @autoclosure () -> String,
                        category: FocusLogCategory = .app,
                        file: String = #fileID,
                        function: String = #function,
                        line: Int = #line) {
        guard isEnabled else { return }
        print(format(level: "WARN",
                     message: message(),
                     category: category,
                     file: file,
                     function: function,
                     line: line))
    }

    static func error(_ message: @autoclosure () -> String,
                      category: FocusLogCategory = .app,
                      file: String = #fileID,
                      function: String = #function,
                      line: Int = #line) {
        guard isEnabled else { return }
        print(format(level: "ERROR",
                     message: message(),
                     category: category,
                     file: file,
                     function: function,
                     line: line))
    }

    private static func format(level: String,
                               message: String,
                               category: FocusLogCategory,
                               file: String,
                               function: String,
                               line: Int) -> String {
        let timestamp = ISO8601DateFormatter().string(from: Date())
        return "[\(timestamp)] [\(level)] [\(category.rawValue)] \(file):\(line) \(function) - \(message)"
    }
}
