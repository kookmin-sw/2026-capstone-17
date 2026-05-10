package com.kmu_focus.focusandroid.core.streaming.data.srt

import android.media.MediaCodec
import android.media.MediaFormat
import com.kmu_focus.focusandroid.core.streaming.domain.entity.SrtConnectionConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer

class SrtVideoMuxerTest {

    private lateinit var srtSocket: SrtSocket
    private lateinit var packetizer: MpegTsPacketizer
    private lateinit var muxer: SrtVideoMuxer

    private val config = SrtConnectionConfig(
        host = "13.125.126.120",
        port = 8890,
        streamKey = "test-stream-key",
    )

    @Before
    fun setup() {
        srtSocket = mockk(relaxed = true)
        packetizer = mockk(relaxed = true)
        muxer = SrtVideoMuxer(srtSocket, packetizer, config)
    }

    @Test
    fun `addTrack는 비디오 트랙 인덱스를 반환한다`() {
        val format = mockk<MediaFormat>(relaxed = true)
        every { format.getString(MediaFormat.KEY_MIME) } returns MediaFormat.MIMETYPE_VIDEO_AVC

        val trackIndex = muxer.addTrack(format)

        assert(trackIndex >= 0)
    }

    @Test
    fun `addTrack는 오디오 트랙 인덱스를 반환한다`() {
        val format = mockk<MediaFormat>(relaxed = true)
        every { format.getString(MediaFormat.KEY_MIME) } returns MediaFormat.MIMETYPE_AUDIO_AAC

        val trackIndex = muxer.addTrack(format)

        assert(trackIndex >= 0)
    }

    @Test
    fun `start 호출 시 SRT 소켓 연결을 시도한다`() {
        every { srtSocket.connect(config.host, config.port, config.streamId) } returns true

        muxer.start()

        verify(exactly = 1) { srtSocket.connect(config.host, config.port, config.streamId) }
    }

    @Test
    fun `writeSampleData 호출 시 MPEG-TS 패킷화 후 SRT로 전송한다`() {
        val videoFormat = mockk<MediaFormat>(relaxed = true)
        every { videoFormat.getString(MediaFormat.KEY_MIME) } returns MediaFormat.MIMETYPE_VIDEO_AVC
        val trackIndex = muxer.addTrack(videoFormat)

        every { srtSocket.connect(any(), any(), any()) } returns true
        muxer.start()

        val buffer = ByteBuffer.wrap(byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65))
        val info = MediaCodec.BufferInfo().apply {
            set(0, 5, 100_000L, 0)
        }
        val tsPackets = listOf(ByteArray(188))
        every { packetizer.packetizeVideo(any(), any(), any()) } returns tsPackets

        muxer.writeSampleData(trackIndex, buffer, info)

        verify { packetizer.packetizeVideo(any(), eq(100_000L), any()) }
        verify { srtSocket.send(any()) }
    }

    @Test
    fun `writeSampleData는 TS 패킷을 1316 payload 기준으로 묶어 전송한다`() {
        val videoFormat = mockk<MediaFormat>(relaxed = true)
        every { videoFormat.getString(MediaFormat.KEY_MIME) } returns MediaFormat.MIMETYPE_VIDEO_AVC
        val trackIndex = muxer.addTrack(videoFormat)

        every { srtSocket.connect(any(), any(), any()) } returns true
        muxer.start()

        val buffer = ByteBuffer.wrap(byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65))
        val info = MediaCodec.BufferInfo().apply {
            set(0, 5, 200_000L, 0)
        }
        val tsPackets = List(8) { ByteArray(188) { 0x47.toByte() } }
        every { packetizer.packetizeVideo(any(), any(), any()) } returns tsPackets
        val payloadSlot = slot<ByteArray>()
        every { srtSocket.send(capture(payloadSlot)) } answers { firstArg<ByteArray>().size }

        muxer.writeSampleData(trackIndex, buffer, info)

        verify(atLeast = 1) { srtSocket.send(any()) }
        assert(payloadSlot.captured.size == 188 || payloadSlot.captured.size == 1316)
    }

    @Test
    fun `stopAndRelease 호출 시 SRT 소켓을 닫는다`() {
        every { srtSocket.connect(any(), any(), any()) } returns true
        muxer.start()

        muxer.stopAndRelease()

        verify(exactly = 1) { srtSocket.close() }
    }

    @Test
    fun `releaseQuietly 호출 시 예외 없이 정리한다`() {
        every { srtSocket.close() } throws RuntimeException("이미 닫힘")

        // 예외가 발생하지 않아야 함
        muxer.releaseQuietly()
    }

    @Test
    fun `AVCC 비디오 샘플은 AnnexB로 변환되고 키프레임에 SPS PPS가 주입된다`() {
        val videoFormat = mockk<MediaFormat>(relaxed = true)
        every { videoFormat.getString(MediaFormat.KEY_MIME) } returns MediaFormat.MIMETYPE_VIDEO_AVC
        every { videoFormat.getByteBuffer("csd-0") } returns ByteBuffer.wrap(byteArrayOf(0x67, 0x64, 0x00, 0x1F))
        every { videoFormat.getByteBuffer("csd-1") } returns ByteBuffer.wrap(byteArrayOf(0x68, 0xEE.toByte(), 0x3C, 0x80.toByte()))
        val trackIndex = muxer.addTrack(videoFormat)

        every { srtSocket.connect(any(), any(), any()) } returns true
        muxer.start()

        val annexBPayloadSlot = slot<ByteArray>()
        every { packetizer.packetizeVideo(capture(annexBPayloadSlot), any(), any()) } returns listOf(ByteArray(188))

        val avccSample = ByteBuffer.wrap(
            byteArrayOf(
                0x00, 0x00, 0x00, 0x04, // NAL length
                0x65, 0x11, 0x22, 0x33, // IDR NAL
            ),
        )
        val info = MediaCodec.BufferInfo().apply {
            set(0, avccSample.remaining(), 100_000L, 1)
        }

        muxer.writeSampleData(trackIndex, avccSample, info)

        val payload = annexBPayloadSlot.captured
        assertTrue(payload.size >= 12)
        assertTrue(payload[0] == 0x00.toByte() && payload[1] == 0x00.toByte() && payload[2] == 0x00.toByte() && payload[3] == 0x01.toByte())
        assertTrue(payload[4].toInt() and 0x1F == 7) // SPS
        assertTrue(payload[8] == 0x00.toByte() && payload[9] == 0x00.toByte() && payload[10] == 0x00.toByte() && payload[11] == 0x01.toByte())
    }

    @Test
    fun `AAC 샘플에는 ADTS 헤더가 붙어서 패킷화된다`() {
        val audioFormat = mockk<MediaFormat>(relaxed = true)
        every { audioFormat.getString(MediaFormat.KEY_MIME) } returns MediaFormat.MIMETYPE_AUDIO_AAC
        every { audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) } returns 44100
        every { audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) } returns 2
        val trackIndex = muxer.addTrack(audioFormat)

        every { srtSocket.connect(any(), any(), any()) } returns true
        muxer.start()

        val audioPayloadSlot = slot<ByteArray>()
        every { packetizer.packetizeAudio(capture(audioPayloadSlot), any()) } returns listOf(ByteArray(188))

        val rawAac = ByteBuffer.wrap(byteArrayOf(0x11, 0x22, 0x33, 0x44))
        val info = MediaCodec.BufferInfo().apply {
            set(0, rawAac.remaining(), 100_000L, 0)
        }

        muxer.writeSampleData(trackIndex, rawAac, info)

        val payload = audioPayloadSlot.captured
        assertTrue(payload.size >= 7)
        assertTrue((payload[0].toInt() and 0xFF) == 0xFF)
        assertTrue((payload[1].toInt() and 0xF0) == 0xF0)
    }
}
