package com.capstone.focus.api.grpc

import com.capstone.focus.common.external.redis.StreamMetadataRedisService
import com.capstone.focus.common.external.redis.model.BoundingBoxRedisPayload
import com.capstone.focus.common.external.redis.model.FaceMetadataRedisPayload
import com.capstone.focus.common.external.redis.model.FrameFaceRedisPayload
import com.capstone.focus.common.external.redis.model.TdmmRawRedisPayload
import com.capstone.focus.grpc.metadata.v1.BoundingBox
import com.capstone.focus.grpc.metadata.v1.FaceMetadataIngestServiceGrpc
import com.capstone.focus.grpc.metadata.v1.PushFaceMetadataRequest
import com.capstone.focus.grpc.metadata.v1.PushFaceMetadataResponse
import com.capstone.focus.grpc.metadata.v1.TdmmRaw
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory
import org.springframework.grpc.server.service.GrpcService

@GrpcService
class FaceMetadataIngestGrpcService(
    private val streamMetadataRedisService: StreamMetadataRedisService
) : FaceMetadataIngestServiceGrpc.FaceMetadataIngestServiceImplBase() {

    private val logger = LoggerFactory.getLogger(FaceMetadataIngestGrpcService::class.java)

    override fun pushFaceMetadata(responseObserver: StreamObserver<PushFaceMetadataResponse>): StreamObserver<PushFaceMetadataRequest> {
        return object : StreamObserver<PushFaceMetadataRequest> {
            private var ingestState = IngestState()

            override fun onNext(frame: PushFaceMetadataRequest) {
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

    private fun createRedisPayload(frame: PushFaceMetadataRequest): FaceMetadataRedisPayload {
        val faces = frame.facesList.map { face ->
            FrameFaceRedisPayload(
                trackingId = face.trackingId,
                boundingBox = if (face.hasBbox()) mapBoundingBox(face.bbox) else null,
                tdmmRaw = if (face.hasTdmmRaw()) mapTdmmRaw(face.tdmmRaw) else null
            )
        }
        return FaceMetadataRedisPayload(
            sessionId = frame.sessionId,
            ptsUs = frame.ptsUs,
            faces = faces
        )
    }

    private fun mapBoundingBox(boundingBox: BoundingBox): BoundingBoxRedisPayload? {
        return BoundingBoxRedisPayload(
            x = boundingBox.x,
            y = boundingBox.y,
            width = boundingBox.width,
            height = boundingBox.height
        )
    }

    private fun mapTdmmRaw(tdmmRaw: TdmmRaw): TdmmRawRedisPayload {
        return TdmmRawRedisPayload(
            coefficients = tdmmRaw.coeffsList
        )
    }

    private fun isValidFrame(frame: PushFaceMetadataRequest): Boolean = frame.sessionId.isNotBlank() && frame.ptsUs >= MINIMUM_VALID_PTS_US

    private fun buildIngestSummary(ingestState: IngestState): PushFaceMetadataResponse {
        return PushFaceMetadataResponse.newBuilder()
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
        private const val MINIMUM_VALID_PTS_US = 0L
    }
}
