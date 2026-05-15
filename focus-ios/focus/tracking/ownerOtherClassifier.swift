//
//  ownerOtherClassifier.swift
//  focus
//
//  Created by Codex on 4/7/26.
//

import Foundation

struct OwnerClassificationResult: Equatable, Sendable {
    let label: TrackLabel
    let ownerID: UUID?
    let similarity: Float
}

final class OwnerOtherClassifier {
    private let similarityThreshold: Float

    init(similarityThreshold: Float = FocusConstants.ownerSimilarityThreshold) {
        self.similarityThreshold = similarityThreshold
    }

    func classify(
        embeddings: [[Float]],
        using store: OwnerEmbeddingStore
    ) -> OwnerClassificationResult {
        let averaged = average(embeddings)
        return classify(embedding: averaged, using: store)
    }

    func classify(
        embedding: [Float],
        using store: OwnerEmbeddingStore
    ) -> OwnerClassificationResult {
        let normalizedEmbedding = l2Normalize(embedding)
        guard !normalizedEmbedding.isEmpty else {
            return OwnerClassificationResult(label: .other, ownerID: nil, similarity: -1)
        }

        let owners = store.allOwners()
        guard !owners.isEmpty else {
            return OwnerClassificationResult(label: .other, ownerID: nil, similarity: -1)
        }

        var bestOwnerID: UUID?
        var bestSimilarity: Float = -1

        for owner in owners {
            var ownerMaxSimilarity: Float = -1

            for ownerEmbedding in owner.embeddings {
                let normalizedOwnerEmbedding = l2Normalize(ownerEmbedding)
                guard !normalizedOwnerEmbedding.isEmpty else { continue }

                let similarity = cosineSimilarity(normalizedEmbedding, normalizedOwnerEmbedding)
                if similarity > ownerMaxSimilarity {
                    ownerMaxSimilarity = similarity
                }
            }

            if ownerMaxSimilarity > bestSimilarity {
                bestSimilarity = ownerMaxSimilarity
                bestOwnerID = owner.id
            }
        }

        if bestSimilarity > similarityThreshold {
            return OwnerClassificationResult(label: .owner, ownerID: bestOwnerID, similarity: bestSimilarity)
        } else {
            return OwnerClassificationResult(label: .other, ownerID: nil, similarity: bestSimilarity)
        }
    }

    private func average(_ samples: [[Float]]) -> [Float] {
        guard let first = samples.first else { return [] }

        var result = Array(repeating: Float(0), count: first.count)
        var validSampleCount = 0

        for sample in samples where sample.count == result.count {
            validSampleCount += 1
            for index in sample.indices {
                result[index] += sample[index]
            }
        }

        guard validSampleCount > 0 else { return [] }
        let count = Float(validSampleCount)
        return result.map { $0 / count }
    }

    private func l2Normalize(_ vector: [Float]) -> [Float] {
        guard !vector.isEmpty else { return [] }

        let norm = sqrt(vector.reduce(0) { partial, value in
            partial + (value * value)
        })

        guard norm > 0 else { return [] }
        return vector.map { $0 / norm }
    }

    private func cosineSimilarity(_ lhs: [Float], _ rhs: [Float]) -> Float {
        guard lhs.count == rhs.count, !lhs.isEmpty else { return -1 }

        var dot: Float = 0
        for index in lhs.indices {
            dot += lhs[index] * rhs[index]
        }

        return dot
    }
}
