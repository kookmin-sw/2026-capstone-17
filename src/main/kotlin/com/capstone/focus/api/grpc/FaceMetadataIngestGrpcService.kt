package com.capstone.focus.api.grpc

import com.capstone.focus.common.external.redis.StreamMetadataRedisService
import com.capstone.focus.common.external.redis.model.FaceMetadataLandmark
import com.capstone.focus.common.external.redis.model.FaceMetadataRedisPayload
import com.capstone.focus.grpc.metadata.v1.FaceMetadataFrame
import com.capstone.focus.grpc.metadata.v1.FaceMetadataIngestServiceGrpc
import com.capstone.focus.grpc.metadata.v1.IngestSummary
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory
import org.springframework.grpc.server.service.GrpcService

@GrpcService
class FaceMetadataIngestGrpcService(
    private val streamMetadataRedisService: StreamMetadataRedisService
) : FaceMetadataIngestServiceGrpc.FaceMetadataIngestServiceImplBase() {

    private val logger = LoggerFactory.getLogger(FaceMetadataIngestGrpcService::class.java)

    override fun pushFaceMetadata(responseObserver: StreamObserver<IngestSummary>): StreamObserver<FaceMetadataFrame> {
        return object : StreamObserver<FaceMetadataFrame> {
            private var ingestState = IngestState()

            override fun onNext(frame: FaceMetadataFrame) {
                ingestState = ingestState.markReceived()
                if (!isValidFrame(frame)) {
                    ingestState = ingestState.markDropped()
                    return
                }
                try {
                    val redisPayload = createRedisPayload(frame)
                    streamMetadataRedisService.saveFaceMetadata(redisPayload)
                    ingestState = ingestState.markAccepted(sessionId = frame.sessionId, ptsUs = frame.ptsUs)
                } catch (exception: Exception) {
                    ingestState = ingestState.markDropped()
                    logger.warn(
                        "Failed to write face metadata to Redis. sessionId={}, ptsUs={}",
                        frame.sessionId,
                        frame.ptsUs,
                        exception
                    )
                }
            }

            override fun onError(throwable: Throwable) {
                logger.warn("Client metadata stream closed with error: {}", throwable.message)
            }

            override fun onCompleted() {
                val summary = buildIngestSummary(ingestState)
                responseObserver.onNext(summary)
                responseObserver.onCompleted()
            }
        }
    }

    private fun createRedisPayload(frame: FaceMetadataFrame): FaceMetadataRedisPayload {
        val trackingId = frame.trackingId.takeIf { it.isNotBlank() }
        val confidence = frame.confidence.takeIf { it > MINIMUM_CONFIDENCE }
        val landmarks = frame.landmarksList.map { landmark ->
            FaceMetadataLandmark(
                x = landmark.x,
                y = landmark.y,
                z = landmark.z
            )
        }
        return FaceMetadataRedisPayload(
            sessionId = frame.sessionId,
            ptsUs = frame.ptsUs,
            avatarUrl = frame.avatarUrl,
            faceData = frame.faceDataMap,
            trackingId = trackingId,
            isReentry = frame.isReentry,
            confidence = confidence,
            boundingBox = frame.bboxList,
            landmarks = landmarks
        )
    }

    private fun isValidFrame(frame: FaceMetadataFrame): Boolean = frame.sessionId.isNotBlank() && frame.ptsUs >= MINIMUM_VALID_PTS_US

    private fun buildIngestSummary(ingestState: IngestState): IngestSummary {
        return IngestSummary.newBuilder()
            .setSessionId(ingestState.latestSessionId)
            .setReceivedFrames(ingestState.receivedFrameCount)
            .setAcceptedFrames(ingestState.acceptedFrameCount)
            .setDroppedFrames(ingestState.droppedFrameCount)
            .setLastPtsUs(ingestState.latestPtsUs)
            .build()
    }

    private data class IngestState(
        val receivedFrameCount: Long = 0L,
        val acceptedFrameCount: Long = 0L,
        val droppedFrameCount: Long = 0L,
        val latestSessionId: String = "",
        val latestPtsUs: Long = 0L
    ) {
        fun markReceived(): IngestState = copy(receivedFrameCount = receivedFrameCount + 1L)
        fun markAccepted(sessionId: String, ptsUs: Long): IngestState =
            copy(acceptedFrameCount = acceptedFrameCount + 1L, latestSessionId = sessionId, latestPtsUs = ptsUs)
        fun markDropped(): IngestState = copy(droppedFrameCount = droppedFrameCount + 1L)
    }

    companion object {
        private const val MINIMUM_VALID_PTS_US = 1L
        private const val MINIMUM_CONFIDENCE = 0f
    }
}
