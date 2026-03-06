package com.capstone.focus.api.grpc.interceptor

import com.capstone.focus.grpc.metadata.v1.PushFaceMetadataRequest
import io.grpc.ForwardingServerCall
import io.grpc.ForwardingServerCallListener
import io.grpc.Grpc
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.grpc.server.GlobalServerInterceptor
import kotlin.math.max

@GlobalServerInterceptor
@Order(Ordered.LOWEST_PRECEDENCE)
class GrpcAccessLogInterceptor : ServerInterceptor {

    private val logger = LoggerFactory.getLogger(GrpcAccessLogInterceptor::class.java)

    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
        val startedAtNanos = System.nanoTime()
        val methodName = call.methodDescriptor.fullMethodName
        val remoteAddress = call.attributes.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR)?.toString() ?: "unknown"
        val callState = CallState()
        val forwardingCall = object : ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
            override fun close(status: Status, trailers: Metadata) {
                val elapsedNanos = max(0L, System.nanoTime() - startedAtNanos)
                val elapsedMillis = elapsedNanos / 1_000_000
                logger.info(
                    "grpc_call_completed method={} status={} duration_ms={} remote={} received_messages={} session_id={}",
                    methodName,
                    status.code.name,
                    elapsedMillis,
                    remoteAddress,
                    callState.receivedMessageCount,
                    callState.sessionId ?: "unknown"
                )
                super.close(status, trailers)
            }
        }
        val listener = next.startCall(forwardingCall, headers)
        return object : ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(listener) {
            override fun onMessage(message: ReqT) {
                callState.receivedMessageCount += 1L
                updateSessionId(callState, message)
                super.onMessage(message)
            }

            override fun onCancel() {
                logger.warn(
                    "grpc_call_cancelled method={} remote={} received_messages={} session_id={}",
                    methodName,
                    remoteAddress,
                    callState.receivedMessageCount,
                    callState.sessionId ?: "unknown"
                )
                super.onCancel()
            }
        }
    }

    private fun <ReqT> updateSessionId(callState: CallState, message: ReqT) {
        if (callState.sessionId != null || message !is PushFaceMetadataRequest) {
            return
        }
        if (message.sessionId.isBlank()) {
            return
        }
        callState.sessionId = message.sessionId
    }

    private data class CallState(
        var receivedMessageCount: Long = 0L,
        var sessionId: String? = null
    )
}
