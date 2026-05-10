package com.kmu_focus.focusandroid.core.streaming.data.srt

import android.media.MediaCodec
import android.media.MediaFormat
import com.kmu_focus.focusandroid.core.media.data.recorder.RealTimeRecorder
import com.kmu_focus.focusandroid.core.streaming.domain.entity.SrtConnectionConfig
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class SrtVideoMuxer(
    private val srtSocket: SrtSocket,
    private val packetizer: MpegTsPacketizer,
    private val config: SrtConnectionConfig,
) : RealTimeRecorder.VideoMuxer {
    private val transportBuffer = ArrayList<ByteArray>(PACKETS_PER_PAYLOAD)

    private val trackTypes = mutableMapOf<Int, TrackType>()
    private val videoCodecConfigs = mutableMapOf<Int, VideoCodecConfig>()
    private val audioConfigs = mutableMapOf<Int, AudioConfig>()
    private var nextTrackIndex = 0
    private var started = false
    private var lastPatPmtPtsUs: Long = Long.MIN_VALUE
    private var hasSentVideoCodecConfig = false

    override fun addTrack(format: MediaFormat): Int {
        val mimeType = format.getString(MediaFormat.KEY_MIME).orEmpty()
        val trackType = when {
            mimeType.startsWith("video/") -> TrackType.VIDEO
            mimeType.startsWith("audio/") -> TrackType.AUDIO
            else -> throw IllegalArgumentException("Unsupported track mime type: $mimeType")
        }
        return nextTrackIndex++.also { trackIndex ->
            trackTypes[trackIndex] = trackType
            when (trackType) {
                TrackType.VIDEO -> videoCodecConfigs[trackIndex] = VideoCodecConfig.from(format)
                TrackType.AUDIO -> audioConfigs[trackIndex] = AudioConfig.from(format)
            }
        }
    }

    override fun start() {
        if (started) {
            return
        }
        val connected = srtSocket.connect(config.host, config.port, config.streamId)
        if (!connected) {
            throw IllegalStateException("Failed to connect to SRT endpoint")
        }
        sendTransportPackets(
            packets = listOf(
                packetizer.generatePAT(),
                packetizer.generatePMT(),
            ),
        )
        started = true
    }

    override fun writeSampleData(
        trackIndex: Int,
        byteBuf: ByteBuffer,
        info: MediaCodec.BufferInfo,
    ) {
        if (!started) {
            return
        }

        val trackType = trackTypes[trackIndex] ?: return
        val sampleData = extractSampleData(byteBuf, info)
        if (sampleData.isEmpty()) {
            return
        }

        val isKeyFrame = trackType == TrackType.VIDEO &&
            info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0

        // 키프레임 또는 일정 간격마다 PAT/PMT 재전송 (수신 측 동기화 유지)
        if (trackType == TrackType.VIDEO) {
            val elapsed = info.presentationTimeUs - lastPatPmtPtsUs
            if (isKeyFrame || elapsed < 0 || elapsed >= PAT_PMT_INTERVAL_US) {
                sendTransportPackets(
                    packets = listOf(packetizer.generatePAT(), packetizer.generatePMT()),
                )
                lastPatPmtPtsUs = info.presentationTimeUs
            }
        }
        val payload = when (trackType) {
            TrackType.VIDEO -> normalizeVideoSample(
                trackIndex = trackIndex,
                sampleData = sampleData,
                isKeyFrame = isKeyFrame,
            )
            TrackType.AUDIO -> normalizeAudioSample(
                trackIndex = trackIndex,
                sampleData = sampleData,
            )
        }

        val packets = when (trackType) {
            TrackType.VIDEO -> packetizer.packetizeVideo(payload, info.presentationTimeUs, isKeyFrame)
            TrackType.AUDIO -> packetizer.packetizeAudio(payload, info.presentationTimeUs)
        }
        sendTransportPackets(packets = packets)
    }

    override fun stopAndRelease() {
        started = false
        srtSocket.close()
    }

    override fun releaseQuietly() {
        runCatching { stopAndRelease() }
    }

    private fun extractSampleData(
        byteBuf: ByteBuffer,
        info: MediaCodec.BufferInfo,
    ): ByteArray {
        val duplicate = byteBuf.duplicate()
        val start = info.offset.coerceAtLeast(0)
        val size = if (info.size > 0) info.size else duplicate.remaining()
        val end = (start + size).coerceAtMost(duplicate.limit())
        duplicate.position(start)
        duplicate.limit(end)
        return ByteArray(duplicate.remaining()).also(duplicate::get)
    }

    private fun sendTransportPackets(packets: List<ByteArray>) {
        if (packets.isEmpty()) {
            return
        }
        transportBuffer.clear()
        packets.forEach { packet ->
            transportBuffer += packet
            if (transportBuffer.size == PACKETS_PER_PAYLOAD) {
                flushTransportBuffer()
            }
        }
        if (transportBuffer.isNotEmpty()) {
            flushTransportBuffer()
        }
    }

    private fun flushTransportBuffer() {
        // SRT LIVE 모드에서 MediaMTX는 각 메시지가 정확히 pkt_size(1316) 바이트를 기대함
        // 7개 미만이면 null TS 패킷(PID=0x1FFF)으로 패딩
        while (transportBuffer.size < PACKETS_PER_PAYLOAD) {
            transportBuffer += NULL_TS_PACKET
        }
        val payload = ByteArray(SRT_PAYLOAD_SIZE)
        var offset = 0
        transportBuffer.forEach { packet ->
            System.arraycopy(packet, 0, payload, offset, packet.size)
            offset += packet.size
        }
        srtSocket.send(payload)
        transportBuffer.clear()
    }

    private fun normalizeVideoSample(
        trackIndex: Int,
        sampleData: ByteArray,
        isKeyFrame: Boolean,
    ): ByteArray {
        val annexBSample = when {
            sampleData.hasAnnexBStartCode() -> sampleData
            else -> convertAvccToAnnexB(sampleData) ?: sampleData
        }
        val codecConfig = videoCodecConfigs[trackIndex] ?: return annexBSample
        if (!isKeyFrame && hasSentVideoCodecConfig) {
            return annexBSample
        }
        if (codecConfig.annexBCodecConfig.isEmpty()) {
            return annexBSample
        }
        if (annexBSample.containsSpsOrPps()) {
            hasSentVideoCodecConfig = true
            return annexBSample
        }
        hasSentVideoCodecConfig = true
        return ByteArray(codecConfig.annexBCodecConfig.size + annexBSample.size).also { merged ->
            System.arraycopy(codecConfig.annexBCodecConfig, 0, merged, 0, codecConfig.annexBCodecConfig.size)
            System.arraycopy(annexBSample, 0, merged, codecConfig.annexBCodecConfig.size, annexBSample.size)
        }
    }

    private fun normalizeAudioSample(
        trackIndex: Int,
        sampleData: ByteArray,
    ): ByteArray {
        if (sampleData.isAdtsFrame()) {
            return sampleData
        }
        val config = audioConfigs[trackIndex] ?: return sampleData
        return config.withAdtsHeader(sampleData)
    }

    private fun convertAvccToAnnexB(sampleData: ByteArray): ByteArray? {
        if (sampleData.size < AVC_NAL_LENGTH_FIELD_SIZE) {
            return null
        }
        val output = ByteArrayOutputStream(sampleData.size + 64)
        var offset = 0
        while (offset + AVC_NAL_LENGTH_FIELD_SIZE <= sampleData.size) {
            val nalLength = ((sampleData[offset].toInt() and 0xFF) shl 24) or
                ((sampleData[offset + 1].toInt() and 0xFF) shl 16) or
                ((sampleData[offset + 2].toInt() and 0xFF) shl 8) or
                (sampleData[offset + 3].toInt() and 0xFF)
            offset += AVC_NAL_LENGTH_FIELD_SIZE
            if (nalLength <= 0 || offset + nalLength > sampleData.size) {
                return null
            }
            output.write(ANNEXB_START_CODE)
            output.write(sampleData, offset, nalLength)
            offset += nalLength
        }
        if (offset != sampleData.size) {
            return null
        }
        return output.toByteArray()
    }

    private fun ByteArray.hasAnnexBStartCode(): Boolean {
        if (size >= 4 &&
            this[0] == 0x00.toByte() &&
            this[1] == 0x00.toByte() &&
            this[2] == 0x00.toByte() &&
            this[3] == 0x01.toByte()
        ) {
            return true
        }
        return size >= 3 &&
            this[0] == 0x00.toByte() &&
            this[1] == 0x00.toByte() &&
            this[2] == 0x01.toByte()
    }

    private fun ByteArray.containsSpsOrPps(): Boolean {
        var index = 0
        while (index + 4 < size) {
            if (this[index] == 0x00.toByte() && this[index + 1] == 0x00.toByte()) {
                val nalStart = when {
                    this[index + 2] == 0x01.toByte() -> index + 3
                    index + 3 < size && this[index + 2] == 0x00.toByte() && this[index + 3] == 0x01.toByte() -> index + 4
                    else -> {
                        index += 1
                        continue
                    }
                }
                if (nalStart < size) {
                    val nalType = this[nalStart].toInt() and 0x1F
                    if (nalType == AVC_NAL_TYPE_SPS || nalType == AVC_NAL_TYPE_PPS) {
                        return true
                    }
                }
            }
            index += 1
        }
        return false
    }

    private fun ByteArray.isAdtsFrame(): Boolean {
        if (size < 2) return false
        return (this[0].toInt() and 0xFF) == 0xFF && (this[1].toInt() and 0xF0) == 0xF0
    }

    private data class VideoCodecConfig(
        val annexBCodecConfig: ByteArray,
    ) {
        companion object {
            fun from(format: MediaFormat): VideoCodecConfig {
                val csd0 = format.getByteBuffer("csd-0")?.let { buffer ->
                    val duplicate = buffer.duplicate()
                    duplicate.position(0)
                    ByteArray(duplicate.remaining()).also(duplicate::get)
                } ?: ByteArray(0)
                val csd1 = format.getByteBuffer("csd-1")?.let { buffer ->
                    val duplicate = buffer.duplicate()
                    duplicate.position(0)
                    ByteArray(duplicate.remaining()).also(duplicate::get)
                } ?: ByteArray(0)
                val annexB = ByteArrayOutputStream(csd0.size + csd1.size + 8).apply {
                    if (csd0.isNotEmpty()) {
                        write(ANNEXB_START_CODE)
                        write(csd0)
                    }
                    if (csd1.isNotEmpty()) {
                        write(ANNEXB_START_CODE)
                        write(csd1)
                    }
                }.toByteArray()
                return VideoCodecConfig(annexBCodecConfig = annexB)
            }
        }
    }

    private data class AudioConfig(
        val sampleRate: Int,
        val channelCount: Int,
    ) {
        fun withAdtsHeader(aacFrame: ByteArray): ByteArray {
            val frameLength = (ADTS_HEADER_SIZE + aacFrame.size).coerceAtMost(MAX_ADTS_FRAME_LENGTH)
            val header = ByteArray(ADTS_HEADER_SIZE)
            val profile = AAC_PROFILE_LC
            val sampleRateIndex = SAMPLE_RATE_INDEX_TABLE[sampleRate] ?: DEFAULT_SAMPLE_RATE_INDEX
            val channelConfig = channelCount.coerceIn(1, 7)

            header[0] = 0xFF.toByte()
            header[1] = 0xF1.toByte()
            header[2] = (((profile - 1) shl 6) or (sampleRateIndex shl 2) or (channelConfig shr 2)).toByte()
            header[3] = (((channelConfig and 0x03) shl 6) or (frameLength shr 11)).toByte()
            header[4] = ((frameLength shr 3) and 0xFF).toByte()
            header[5] = (((frameLength and 0x07) shl 5) or 0x1F).toByte()
            header[6] = 0xFC.toByte()

            val payloadLength = (frameLength - ADTS_HEADER_SIZE).coerceAtLeast(0).coerceAtMost(aacFrame.size)
            return ByteArray(ADTS_HEADER_SIZE + payloadLength).also { output ->
                System.arraycopy(header, 0, output, 0, ADTS_HEADER_SIZE)
                if (payloadLength > 0) {
                    System.arraycopy(aacFrame, 0, output, ADTS_HEADER_SIZE, payloadLength)
                }
            }
        }

        companion object {
            fun from(format: MediaFormat): AudioConfig {
                val sampleRate = runCatching {
                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }.getOrDefault(44_100)
                val channelCount = runCatching {
                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }.getOrDefault(2)
                return AudioConfig(
                    sampleRate = sampleRate,
                    channelCount = channelCount,
                )
            }
        }
    }

    private enum class TrackType {
        VIDEO,
        AUDIO,
    }

    private companion object {
        const val TS_PACKET_SIZE = 188
        const val SRT_PAYLOAD_SIZE = 1316
        const val PACKETS_PER_PAYLOAD = SRT_PAYLOAD_SIZE / TS_PACKET_SIZE
        const val PAT_PMT_INTERVAL_US = 500_000L // 500ms
        const val AVC_NAL_TYPE_SPS = 7
        const val AVC_NAL_TYPE_PPS = 8
        const val AVC_NAL_LENGTH_FIELD_SIZE = 4
        const val ADTS_HEADER_SIZE = 7
        const val MAX_ADTS_FRAME_LENGTH = 8191
        const val AAC_PROFILE_LC = 2
        const val DEFAULT_SAMPLE_RATE_INDEX = 4 // 44100
        val ANNEXB_START_CODE = byteArrayOf(0x00, 0x00, 0x00, 0x01)
        val SAMPLE_RATE_INDEX_TABLE = mapOf(
            96000 to 0,
            88200 to 1,
            64000 to 2,
            48000 to 3,
            44100 to 4,
            32000 to 5,
            24000 to 6,
            22050 to 7,
            16000 to 8,
            12000 to 9,
            11025 to 10,
            8000 to 11,
            7350 to 12,
        )

        // PID=0x1FFF null TS 패킷 — 수신 측에서 무시되며, SRT 페이로드 크기 맞춤용
        val NULL_TS_PACKET = ByteArray(TS_PACKET_SIZE).also { packet ->
            packet[0] = 0x47 // sync byte
            packet[1] = 0x1F // PID 0x1FFF high (TEI=0, PUSI=0, Priority=0)
            packet[2] = 0xFF.toByte() // PID 0x1FFF low
            packet[3] = 0x10 // adaptation_field_control=01, CC=0
            for (i in 4 until TS_PACKET_SIZE) packet[i] = 0xFF.toByte()
        }
    }
}
