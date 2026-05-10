package com.kmu_focus.focusandroid.core.streaming.data.srt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer

class MpegTsPacketizerTest {

    private lateinit var packetizer: MpegTsPacketizer

    @Before
    fun setup() {
        packetizer = MpegTsPacketizer()
    }

    @Test
    fun `TS 패킷은 188바이트이다`() {
        val patPacket = packetizer.generatePAT()

        assertEquals(MpegTsPacketizer.TS_PACKET_SIZE, patPacket.size)
    }

    @Test
    fun `PAT 패킷의 sync byte는 0x47이다`() {
        val patPacket = packetizer.generatePAT()

        assertEquals(0x47.toByte(), patPacket[0])
    }

    @Test
    fun `PMT 패킷의 sync byte는 0x47이다`() {
        val pmtPacket = packetizer.generatePMT(
            videoPid = MpegTsPacketizer.DEFAULT_VIDEO_PID,
            audioPid = MpegTsPacketizer.DEFAULT_AUDIO_PID,
        )

        assertEquals(0x47.toByte(), pmtPacket[0])
    }

    @Test
    fun `H264 NAL을 PES로 패킷화하면 sync byte로 시작한다`() {
        val nalUnit = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65, 0x01, 0x02, 0x03)
        val ptsUs = 100_000L

        val packets = packetizer.packetizeVideo(nalUnit, ptsUs)

        assertTrue(packets.isNotEmpty())
        packets.forEach { packet ->
            assertEquals(MpegTsPacketizer.TS_PACKET_SIZE, packet.size)
            assertEquals(0x47.toByte(), packet[0])
        }
    }

    @Test
    fun `AAC 프레임을 PES로 패킷화하면 sync byte로 시작한다`() {
        val aacFrame = ByteArray(128) { it.toByte() }
        val ptsUs = 100_000L

        val packets = packetizer.packetizeAudio(aacFrame, ptsUs)

        assertTrue(packets.isNotEmpty())
        packets.forEach { packet ->
            assertEquals(MpegTsPacketizer.TS_PACKET_SIZE, packet.size)
            assertEquals(0x47.toByte(), packet[0])
        }
    }

    @Test
    fun `큰 NAL 유닛은 여러 TS 패킷으로 분할된다`() {
        // 188 - 4(header) - 약20(PES header) = ~164 바이트 payload per packet
        val largeNal = ByteArray(500) { it.toByte() }
        val ptsUs = 200_000L

        val packets = packetizer.packetizeVideo(largeNal, ptsUs)

        assertTrue(packets.size > 1)
    }

    @Test
    fun `작은 PES 마지막 패킷은 adaptation field stuffing을 사용한다`() {
        val aacFrame = ByteArray(16) { it.toByte() }

        val packets = packetizer.packetizeAudio(aacFrame, 100_000L)
        val lastPacket = packets.last()
        val adaptationControl = (lastPacket[3].toInt() shr 4) and 0x03
        val adaptationFieldLength = lastPacket[4].toInt() and 0xFF

        assertEquals(0x03, adaptationControl)
        assertTrue(adaptationFieldLength > 0)
    }

    @Test
    fun `continuity counter는 호출마다 증가한다`() {
        val nal1 = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65, 0x01)
        val nal2 = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65, 0x02)

        val packets1 = packetizer.packetizeVideo(nal1, 100_000L)
        val packets2 = packetizer.packetizeVideo(nal2, 200_000L)

        // continuity counter는 하위 4비트
        val cc1 = packets1.first()[3].toInt() and 0x0F
        val cc2 = packets2.first()[3].toInt() and 0x0F
        assertEquals((cc1 + 1) % 16, cc2)
    }

    @Test
    fun `비디오 패킷의 첫 TS 패킷에는 PCR이 포함된다`() {
        val nalUnit = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65, 0x01, 0x02, 0x03)
        val ptsUs = 100_000L

        val packets = packetizer.packetizeVideo(nalUnit, ptsUs)
        val firstPacket = packets.first()

        // adaptation_field_control = 11 (adaptation + payload)
        val adaptationControl = (firstPacket[3].toInt() shr 4) and 0x03
        assertEquals(0x03, adaptationControl)

        // adaptation field flags의 PCR flag (bit 4) 확인
        val adaptationFlags = firstPacket[5].toInt() and 0xFF
        assertTrue("PCR flag가 설정되어야 한다", (adaptationFlags and 0x10) != 0)
    }

    @Test
    fun `키프레임 비디오 패킷에는 random access indicator가 설정된다`() {
        val nalUnit = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65, 0x01, 0x02, 0x03)
        val ptsUs = 100_000L

        val packets = packetizer.packetizeVideo(nalUnit, ptsUs, isKeyFrame = true)
        val firstPacket = packets.first()

        // adaptation field flags의 random_access_indicator (bit 6) 확인
        val adaptationFlags = firstPacket[5].toInt() and 0xFF
        assertTrue("random access indicator가 설정되어야 한다", (adaptationFlags and 0x40) != 0)
    }

    @Test
    fun `비키프레임 비디오 패킷에는 random access indicator가 설정되지 않는다`() {
        val nalUnit = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65, 0x01, 0x02, 0x03)
        val ptsUs = 100_000L

        val packets = packetizer.packetizeVideo(nalUnit, ptsUs, isKeyFrame = false)
        val firstPacket = packets.first()

        val adaptationFlags = firstPacket[5].toInt() and 0xFF
        assertTrue("random access indicator가 설정되지 않아야 한다", (adaptationFlags and 0x40) == 0)
    }
}
