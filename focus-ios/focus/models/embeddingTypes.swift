//
//  embeddingTypes.swift
//  focus
//
//  Created by 이동언 on 3/8/26.
//

import Foundation

struct FaceEmbedding: Equatable, Sendable {
    let vector: [Float]

    var dimension: Int {
        vector.count
    }

    var isEmpty: Bool {
        vector.isEmpty
    }
}
