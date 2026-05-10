package com.kmu_focus.focusandroid.core.grpc.data.remote

import com.kmu_focus.focusandroid.core.grpc.proto.FaceMetadataIngestServiceGrpc
import com.kmu_focus.focusandroid.core.grpc.proto.PushFaceMetadataRequest
import com.kmu_focus.focusandroid.core.grpc.proto.PushFaceMetadataResponse
import io.grpc.stub.StreamObserver
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class FaceMetadataStreamManagerTest {

    private lateinit var asyncStub: FaceMetadataIngestServiceGrpc.FaceMetadataIngestServiceStub
    private lateinit var requestObserver1: StreamObserver<PushFaceMetadataRequest>
    private lateinit var requestObserver2: StreamObserver<PushFaceMetadataRequest>
    private lateinit var requestObserver3: StreamObserver<PushFaceMetadataRequest>
    private lateinit var manager: FaceMetadataStreamManager

    private val responseObservers = mutableListOf<StreamObserver<PushFaceMetadataResponse>>()

    @Before
    fun setup() {
        asyncStub = mockk()
        requestObserver1 = mockk(relaxed = true)
        requestObserver2 = mockk(relaxed = true)
        requestObserver3 = mockk(relaxed = true)

        every { asyncStub.pushFaceMetadata(capture(responseObservers)) } returnsMany listOf(
            requestObserver1,
            requestObserver2,
            requestObserver3,
        )

        manager = FaceMetadataStreamManager(asyncStub)
    }

    @Test
    fun `같은 sessionId의 프레임은 기존 스트림을 재사용한다`() {
        manager.sendFrame(frame(sessionId = "broadcast-1", ptsUs = 100_000L))
        manager.sendFrame(frame(sessionId = "broadcast-1", ptsUs = 133_333L))

        verify(exactly = 1) { asyncStub.pushFaceMetadata(any()) }
        verify(exactly = 2) { requestObserver1.onNext(any()) }
        verify(exactly = 0) { requestObserver1.onCompleted() }
    }

    @Test
    fun `sessionId가 바뀌면 이전 스트림을 닫고 새 스트림을 연다`() {
        manager.sendFrame(frame(sessionId = "broadcast-1", ptsUs = 100_000L))
        manager.sendFrame(frame(sessionId = "broadcast-2", ptsUs = 133_333L))

        verify(exactly = 2) { asyncStub.pushFaceMetadata(any()) }
        verify(exactly = 1) { requestObserver1.onCompleted() }
        verify(exactly = 1) {
            requestObserver2.onNext(match { it.sessionId == "broadcast-2" })
        }
    }

    @Test
    fun `이전 스트림의 완료 콜백이 새 스트림 상태를 지우지 않는다`() {
        manager.sendFrame(frame(sessionId = "broadcast-1", ptsUs = 100_000L))
        manager.sendFrame(frame(sessionId = "broadcast-2", ptsUs = 133_333L))

        responseObservers.first().onCompleted()
        manager.sendFrame(frame(sessionId = "broadcast-2", ptsUs = 166_666L))

        verify(exactly = 2) { asyncStub.pushFaceMetadata(any()) }
        verify(exactly = 2) { requestObserver2.onNext(any()) }
        verify(exactly = 0) { requestObserver3.onNext(any()) }
    }

    @Test
    fun `complete 이후 다시 전송하면 새 스트림을 연다`() {
        manager.sendFrame(frame(sessionId = "broadcast-1", ptsUs = 100_000L))

        manager.complete()
        manager.sendFrame(frame(sessionId = "broadcast-1", ptsUs = 133_333L))

        verify(exactly = 2) { asyncStub.pushFaceMetadata(any()) }
        verify(exactly = 1) { requestObserver1.onCompleted() }
        verify(exactly = 1) {
            requestObserver2.onNext(match { it.ptsUs == 133_333L })
        }
    }

    private fun frame(
        sessionId: String,
        ptsUs: Long,
    ): PushFaceMetadataRequest {
        return PushFaceMetadataRequest.newBuilder()
            .setSessionId(sessionId)
            .setPtsUs(ptsUs)
            .build()
    }
}
