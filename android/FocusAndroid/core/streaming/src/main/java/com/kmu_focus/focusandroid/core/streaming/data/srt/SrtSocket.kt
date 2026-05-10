package com.kmu_focus.focusandroid.core.streaming.data.srt

import io.github.thibaultbee.srtdroid.core.enums.SockOpt
import io.github.thibaultbee.srtdroid.core.enums.Transtype

interface SrtSocket {
    fun connect(
        host: String,
        port: Int,
        streamId: String,
    ): Boolean

    fun send(data: ByteArray): Int

    fun close()
}

internal class DefaultSrtSocket : SrtSocket {
    private val socket = io.github.thibaultbee.srtdroid.core.models.SrtSocket()

    override fun connect(
        host: String,
        port: Int,
        streamId: String,
    ): Boolean {
        if (!socket.isValid) {
            return false
        }
        return runCatching {
            socket.setSockFlag(SockOpt.TRANSTYPE, Transtype.LIVE)
            socket.setSockFlag(SockOpt.STREAMID, streamId)
            socket.setSockFlag(SockOpt.PAYLOADSIZE, DEFAULT_PAYLOAD_SIZE)
            socket.setSockFlag(SockOpt.LATENCY, DEFAULT_LATENCY_MS)
            socket.setSockFlag(SockOpt.PEERLATENCY, DEFAULT_LATENCY_MS)
            socket.setSockFlag(SockOpt.RCVLATENCY, DEFAULT_LATENCY_MS)
            socket.connect(host, port)
            check(socket.isValid) { "SRT socket became invalid after connect" }
            check(socket.isConnected) { "SRT socket is not connected after connect state=${describeState()}" }
            true
        }.getOrDefault(false)
    }

    override fun send(data: ByteArray): Int {
        if (!socket.isValid || !socket.isConnected) {
            throw IllegalStateException("SRT socket is unavailable state=${describeState()}")
        }
        return runCatching { socket.send(data) }
            .mapCatching { sentBytes ->
                check(sentBytes == data.size) {
                    "SRT partial send sent=$sentBytes expected=${data.size} state=${describeState()}"
                }
                sentBytes
            }
            .getOrThrow()
    }

    override fun close() {
        runCatching { socket.close() }
    }

    private fun describeState(): String {
        val valid = runCatching { socket.isValid }.getOrDefault(false)
        val connected = runCatching { socket.isConnected }.getOrDefault(false)
        val state = runCatching { socket.sockState.toString() }.getOrDefault("unknown")
        return "valid=$valid connected=$connected sockState=$state"
    }

    private companion object {
        const val DEFAULT_PAYLOAD_SIZE = 1316
        const val DEFAULT_LATENCY_MS = 120
    }
}
