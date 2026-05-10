package com.kmu_focus.focusandroid.core.grpc.data.remote

import com.kmu_focus.focusandroid.core.grpc.proto.FaceMetadataIngestServiceGrpc
import com.kmu_focus.focusandroid.core.grpc.proto.PushFaceMetadataRequest
import com.kmu_focus.focusandroid.core.grpc.proto.PushFaceMetadataResponse
import io.grpc.stub.StreamObserver

class FaceMetadataStreamManager(
    private val asyncStub: FaceMetadataIngestServiceGrpc.FaceMetadataIngestServiceStub,
) {
    private val lock = Any()
    private var requestObserver: StreamObserver<PushFaceMetadataRequest>? = null
    private var currentSessionId: String? = null

    fun start(sessionId: String) {
        synchronized(lock) {
            if (requestObserver != null && currentSessionId == sessionId) {
                return
            }

            requestObserver?.onCompleted()
            openStreamLocked(sessionId)
        }
    }

    fun sendFrame(frame: PushFaceMetadataRequest) {
        synchronized(lock) {
            if (requestObserver == null || currentSessionId != frame.sessionId) {
                requestObserver?.onCompleted()
                openStreamLocked(frame.sessionId)
            }

            requestObserver?.onNext(frame)
        }
    }

    fun complete() {
        synchronized(lock) {
            requestObserver?.onCompleted()
            requestObserver = null
            currentSessionId = null
        }
    }

    private fun openStreamLocked(sessionId: String) {
        lateinit var nextRequestObserver: StreamObserver<PushFaceMetadataRequest>
        nextRequestObserver = asyncStub.pushFaceMetadata(
            object : StreamObserver<PushFaceMetadataResponse> {
                override fun onNext(value: PushFaceMetadataResponse) = Unit

                override fun onError(t: Throwable) {
                    synchronized(lock) {
                        clearStreamLocked(nextRequestObserver)
                    }
                }

                override fun onCompleted() {
                    synchronized(lock) {
                        clearStreamLocked(nextRequestObserver)
                    }
                }
            }
        )

        requestObserver = nextRequestObserver
        currentSessionId = sessionId
    }

    private fun clearStreamLocked(observer: StreamObserver<PushFaceMetadataRequest>) {
        if (requestObserver === observer) {
            requestObserver = null
            currentSessionId = null
        }
    }
}
