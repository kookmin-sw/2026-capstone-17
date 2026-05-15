//
//  tdmmTypes.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation

struct TDMMCoefficients: Equatable, Sendable {
    let id: [Float]      // 219
    let exp: [Float]     // 39
    let pose: [Float]    // 6
    let extra: [Float]   // 1

    static let idCount = 219
    static let expCount = 39
    static let poseCount = 6
    static let extraCount = 1
    static let totalCount = 265

    init(id: [Float], exp: [Float], pose: [Float], extra: [Float]) {
        self.id = id
        self.exp = exp
        self.pose = pose
        self.extra = extra
    }

    init?(merged: [Float]) {
        guard merged.count == Self.totalCount else { return nil }

        self.id = Array(merged[0..<Self.idCount])
        self.exp = Array(merged[Self.idCount..<(Self.idCount + Self.expCount)])
        self.pose = Array(merged[(Self.idCount + Self.expCount)..<(Self.idCount + Self.expCount + Self.poseCount)])
        self.extra = Array(merged[(Self.totalCount - Self.extraCount)..<Self.totalCount])
    }

    var merged: [Float] {
        id + exp + pose + extra
    }

    var isValidLayout: Bool {
        id.count == Self.idCount &&
        exp.count == Self.expCount &&
        pose.count == Self.poseCount &&
        extra.count == Self.extraCount
    }

    var idVector: [Float] {
        id
    }
}
