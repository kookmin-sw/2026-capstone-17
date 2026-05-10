package com.kmu_focus.focusandroid.core.streaming.data.srt

import java.io.ByteArrayOutputStream
import kotlin.math.min

class MpegTsPacketizer {

    private val continuityCounters = mutableMapOf<Int, Int>()

    fun generatePAT(): ByteArray {
        val sectionWithoutCrc = byteArrayOf(
            0x00,
            0xB0.toByte(), 0x0D,
            0x00, 0x01,
            0xC1.toByte(),
            0x00,
            0x00,
            0x00, 0x01,
            (0xE0 or ((PMT_PID shr 8) and 0x1F)).toByte(),
            (PMT_PID and 0xFF).toByte(),
        )
        return createPsiPacket(PAT_PID, appendCrc(sectionWithoutCrc))
    }

    fun generatePMT(
        videoPid: Int = DEFAULT_VIDEO_PID,
        audioPid: Int = DEFAULT_AUDIO_PID,
    ): ByteArray {
        val sectionWithoutCrc = byteArrayOf(
            0x02,
            0xB0.toByte(), 0x17,
            0x00, 0x01,
            0xC1.toByte(),
            0x00,
            0x00,
            (0xE0 or ((videoPid shr 8) and 0x1F)).toByte(),
            (videoPid and 0xFF).toByte(),
            0xF0.toByte(), 0x00,
            0x1B,
            (0xE0 or ((videoPid shr 8) and 0x1F)).toByte(),
            (videoPid and 0xFF).toByte(),
            0xF0.toByte(), 0x00,
            0x0F,
            (0xE0 or ((audioPid shr 8) and 0x1F)).toByte(),
            (audioPid and 0xFF).toByte(),
            0xF0.toByte(), 0x00,
        )
        return createPsiPacket(PMT_PID, appendCrc(sectionWithoutCrc))
    }

    fun packetizeVideo(
        nalUnit: ByteArray,
        ptsUs: Long,
        isKeyFrame: Boolean = false,
    ): List<ByteArray> {
        val pts90kHz = (ptsUs * 90L) / 1000L
        val pesPayload = buildPesPacket(
            streamId = VIDEO_STREAM_ID,
            elementaryStream = nalUnit,
            ptsUs = ptsUs,
            useUnboundedPacketLength = true,
        )
        return packetizePes(
            pid = DEFAULT_VIDEO_PID,
            pesPayload = pesPayload,
            pcr90kHz = pts90kHz,
            randomAccessIndicator = isKeyFrame,
        )
    }

    fun packetizeAudio(
        aacFrame: ByteArray,
        ptsUs: Long,
    ): List<ByteArray> {
        val pesPayload = buildPesPacket(
            streamId = AUDIO_STREAM_ID,
            elementaryStream = aacFrame,
            ptsUs = ptsUs,
            useUnboundedPacketLength = false,
        )
        return packetizePes(pid = DEFAULT_AUDIO_PID, pesPayload = pesPayload)
    }

    private fun createPsiPacket(
        pid: Int,
        section: ByteArray,
    ): ByteArray {
        val packet = createTsPacketHeader(pid = pid, payloadUnitStart = true)
        packet[4] = 0x00
        System.arraycopy(section, 0, packet, 5, min(section.size, TS_PACKET_SIZE - 5))
        return packet
    }

    private fun buildPesPacket(
        streamId: Int,
        elementaryStream: ByteArray,
        ptsUs: Long,
        useUnboundedPacketLength: Boolean,
    ): ByteArray {
        val header = ByteArrayOutputStream()
        val pts = (ptsUs * 90L) / 1000L
        header.write(byteArrayOf(0x00, 0x00, 0x01, streamId.toByte()))

        val packetLength = if (useUnboundedPacketLength) {
            0
        } else {
            (3 + 5 + elementaryStream.size).coerceAtMost(0xFFFF)
        }
        header.write(byteArrayOf(((packetLength shr 8) and 0xFF).toByte(), (packetLength and 0xFF).toByte()))
        header.write(byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x05))
        header.write(encodePts(pts))
        header.write(elementaryStream)
        return header.toByteArray()
    }

    private fun packetizePes(
        pid: Int,
        pesPayload: ByteArray,
        pcr90kHz: Long? = null,
        randomAccessIndicator: Boolean = false,
    ): List<ByteArray> {
        val packets = mutableListOf<ByteArray>()
        var offset = 0
        var isFirstPacket = true
        while (offset < pesPayload.size) {
            val remainingBytes = pesPayload.size - offset
            if (isFirstPacket && pcr90kHz != null) {
                val copyLength = min(MAX_PAYLOAD_WITH_PCR, remainingBytes)
                val payloadStartIndex = createPacketWithPcrAdaptation(
                    pid = pid,
                    payloadUnitStart = true,
                    pcr90kHz = pcr90kHz,
                    randomAccessIndicator = randomAccessIndicator,
                    payloadSize = copyLength,
                )
                val packet = transportPacketBuffer
                System.arraycopy(pesPayload, offset, packet, payloadStartIndex, copyLength)
                offset += copyLength
                packets += packet.copyOf()
                isFirstPacket = false
            } else {
                val copyLength = min(MAX_TS_PAYLOAD_SIZE, remainingBytes)
                val payloadUnitStart = isFirstPacket && offset == 0
                val payloadStartIndex = if (copyLength == MAX_TS_PAYLOAD_SIZE) {
                    createPayloadOnlyPacket(pid = pid, payloadUnitStart = payloadUnitStart)
                    TS_HEADER_SIZE
                } else {
                    createStuffedPacket(pid = pid, payloadUnitStart = payloadUnitStart, payloadSize = copyLength)
                }
                val packet = transportPacketBuffer
                System.arraycopy(pesPayload, offset, packet, payloadStartIndex, copyLength)
                offset += copyLength
                packets += packet.copyOf()
                isFirstPacket = false
            }
        }
        return packets
    }

    private fun createPayloadOnlyPacket(
        pid: Int,
        payloadUnitStart: Boolean,
    ) {
        val packet = transportPacketBuffer
        packet.fill(0xFF.toByte())
        val continuityCounter = nextContinuityCounter(pid)
        packet[0] = SYNC_BYTE
        packet[1] = (((if (payloadUnitStart) 0x40 else 0x00) or ((pid shr 8) and 0x1F))).toByte()
        packet[2] = (pid and 0xFF).toByte()
        packet[3] = (0x10 or continuityCounter).toByte()
    }

    private fun createStuffedPacket(
        pid: Int,
        payloadUnitStart: Boolean,
        payloadSize: Int,
    ): Int {
        val packet = transportPacketBuffer
        packet.fill(0xFF.toByte())
        val continuityCounter = nextContinuityCounter(pid)
        val adaptationFieldLength = MAX_TS_PAYLOAD_SIZE - payloadSize - 1
        packet[0] = SYNC_BYTE
        packet[1] = (((if (payloadUnitStart) 0x40 else 0x00) or ((pid shr 8) and 0x1F))).toByte()
        packet[2] = (pid and 0xFF).toByte()
        packet[3] = (0x30 or continuityCounter).toByte()
        packet[4] = adaptationFieldLength.toByte()
        if (adaptationFieldLength > 0) {
            packet[5] = 0x00
            for (index in 6 until 5 + adaptationFieldLength) {
                packet[index] = 0xFF.toByte()
            }
        }
        return TS_HEADER_SIZE + 1 + adaptationFieldLength
    }

    /**
     * PCR + optional RAI가 포함된 adaptation field를 가진 TS 패킷을 생성한다.
     * payload 공간이 MAX_PAYLOAD_WITH_PCR(176)보다 작으면 stuffing으로 채운다.
     * @return payload 시작 인덱스
     */
    private fun createPacketWithPcrAdaptation(
        pid: Int,
        payloadUnitStart: Boolean,
        pcr90kHz: Long,
        randomAccessIndicator: Boolean,
        payloadSize: Int,
    ): Int {
        val packet = transportPacketBuffer
        packet.fill(0xFF.toByte())
        val continuityCounter = nextContinuityCounter(pid)

        packet[0] = SYNC_BYTE
        packet[1] = (((if (payloadUnitStart) 0x40 else 0x00) or ((pid shr 8) and 0x1F))).toByte()
        packet[2] = (pid and 0xFF).toByte()
        // adaptation_field_control = 11 (adaptation + payload)
        packet[3] = (0x30 or continuityCounter).toByte()

        val stuffingNeeded = (MAX_PAYLOAD_WITH_PCR - payloadSize).coerceAtLeast(0)
        val adaptationFieldLength = PCR_ADAPTATION_CONTENT_SIZE + stuffingNeeded
        packet[4] = adaptationFieldLength.toByte()

        // flags: PCR flag (bit 4) + optional random_access_indicator (bit 6)
        var flags = 0x10
        if (randomAccessIndicator) flags = flags or 0x40
        packet[5] = flags.toByte()

        // PCR (6 bytes): 33-bit base + 6 reserved bits(1) + 9-bit extension(0)
        encodePcrInto(packet, 6, pcr90kHz)

        // stuffing bytes는 이미 0xFF로 채워져 있음 (fill)
        return TS_PACKET_SIZE - payloadSize
    }

    private fun encodePcrInto(packet: ByteArray, offset: Int, pcr90kHz: Long) {
        val pcrBase = pcr90kHz
        val pcrExt = 0
        packet[offset] = ((pcrBase shr 25) and 0xFF).toByte()
        packet[offset + 1] = ((pcrBase shr 17) and 0xFF).toByte()
        packet[offset + 2] = ((pcrBase shr 9) and 0xFF).toByte()
        packet[offset + 3] = ((pcrBase shr 1) and 0xFF).toByte()
        // bit 7: PCR base LSB, bits 6-1: reserved (1), bit 0: PCR ext MSB
        packet[offset + 4] = (((pcrBase and 0x01).toInt() shl 7) or 0x7E or ((pcrExt shr 8) and 0x01)).toByte()
        packet[offset + 5] = (pcrExt and 0xFF).toByte()
    }

    private fun createTsPacketHeader(
        pid: Int,
        payloadUnitStart: Boolean,
    ): ByteArray {
        val packet = ByteArray(TS_PACKET_SIZE) { 0xFF.toByte() }
        val continuityCounter = nextContinuityCounter(pid)
        packet[0] = SYNC_BYTE
        packet[1] = (((if (payloadUnitStart) 0x40 else 0x00) or ((pid shr 8) and 0x1F))).toByte()
        packet[2] = (pid and 0xFF).toByte()
        packet[3] = (0x10 or continuityCounter).toByte()
        return packet
    }

    private fun nextContinuityCounter(pid: Int): Int {
        val current = continuityCounters[pid] ?: 0
        continuityCounters[pid] = (current + 1) % CONTINUITY_COUNTER_MODULO
        return current
    }

    private fun appendCrc(sectionWithoutCrc: ByteArray): ByteArray {
        val crc = crc32Mpeg(sectionWithoutCrc)
        return sectionWithoutCrc + byteArrayOf(
            ((crc shr 24) and 0xFF).toByte(),
            ((crc shr 16) and 0xFF).toByte(),
            ((crc shr 8) and 0xFF).toByte(),
            (crc and 0xFF).toByte(),
        )
    }

    private fun encodePts(pts: Long): ByteArray {
        val ptsHigh = ((pts shr 30) and 0x07).toInt()
        val ptsMid = ((pts shr 15) and 0x7F).toInt()
        val ptsLow = (pts and 0x7F).toInt()
        return byteArrayOf(
            (0x20 or (ptsHigh shl 1) or 0x01).toByte(),
            ((pts shr 22) and 0xFF).toByte(),
            ((ptsMid shl 1) or 0x01).toByte(),
            ((pts shr 7) and 0xFF).toByte(),
            ((ptsLow shl 1) or 0x01).toByte(),
        )
    }

    private fun crc32Mpeg(data: ByteArray): Int {
        var crc = -0x1
        data.forEach { byte ->
            crc = crc xor ((byte.toInt() and 0xFF) shl 24)
            repeat(8) {
                crc = if ((crc and 0x80000000.toInt()) != 0) {
                    (crc shl 1) xor 0x04C11DB7
                } else {
                    crc shl 1
                }
            }
        }
        return crc
    }

    companion object {
        const val TS_PACKET_SIZE = 188
        const val DEFAULT_VIDEO_PID = 0x101
        const val DEFAULT_AUDIO_PID = 0x102

        private const val TS_HEADER_SIZE = 4
        private const val MAX_TS_PAYLOAD_SIZE = TS_PACKET_SIZE - TS_HEADER_SIZE
        // adaptation_field: 1 byte flags + 6 bytes PCR = 7 bytes content
        private const val PCR_ADAPTATION_CONTENT_SIZE = 7
        // adaptation_field_length byte(1) + content(7) = 8 bytes overhead
        private const val MAX_PAYLOAD_WITH_PCR = MAX_TS_PAYLOAD_SIZE - 1 - PCR_ADAPTATION_CONTENT_SIZE
        private const val PAT_PID = 0x0000
        private const val PMT_PID = 0x0100
        private const val VIDEO_STREAM_ID = 0xE0
        private const val AUDIO_STREAM_ID = 0xC0
        private const val CONTINUITY_COUNTER_MODULO = 16
        private val SYNC_BYTE = 0x47.toByte()
    }

    private val transportPacketBuffer = ByteArray(TS_PACKET_SIZE)
}
