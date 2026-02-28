package com.kmu_focus.focusandroid.core.media.data.recorder

import android.media.MediaFormat
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class AudioTrackExtractorTest {

    @Test
    fun `hasAudio returns true when source has audio track`() {
        val fixture = createExtractorFixture(hasAudio = true)

        assertTrue(fixture.extractor.hasAudio)
    }

    @Test
    fun `hasAudio returns false when source has no audio track`() {
        val fixture = createExtractorFixture(hasAudio = false)

        assertFalse(fixture.extractor.hasAudio)
    }

    @Test
    fun `format returns audio track MediaFormat`() {
        val expectedFormat = mockMediaFormat(MediaFormat.MIMETYPE_AUDIO_AAC)
        val fixture = createExtractorFixture(hasAudio = true, audioFormat = expectedFormat)

        val format = fixture.extractor.format
        assertNotNull(format)
        assertEquals(MediaFormat.MIMETYPE_AUDIO_AAC, format?.getString(MediaFormat.KEY_MIME))
    }

    @Test
    fun `seekTo positions extractor to specified timestamp`() {
        val fixture = createExtractorFixture(hasAudio = true)
        val startTimeUs = 5_000_000L

        fixture.extractor.seekTo(startTimeUs)

        assertEquals(startTimeUs, fixture.backend.lastSeekTimeUs)
    }

    @Test
    fun `readSampleData returns sample with rebased timestamp`() {
        val fixture = createExtractorFixture(
            hasAudio = true,
            samples = listOf(
                FakeAudioSample(timeUs = 5_100_000L, size = 8),
                FakeAudioSample(timeUs = 5_133_000L, size = 8),
            ),
        )
        fixture.extractor.seekTo(5_000_000L)

        val sample = fixture.extractor.readNextSample()

        assertNotNull(sample)
        assertEquals(100_000L, sample?.presentationTimeUs)
    }

    @Test
    fun `readNextSample returns null when no more samples`() {
        val fixture = createExtractorFixture(hasAudio = true, samples = emptyList())

        val sample = fixture.extractor.readNextSample()

        assertNull(sample)
    }

    @Test
    fun `release cleans up internal MediaExtractor`() {
        val fixture = createExtractorFixture(hasAudio = true)

        fixture.extractor.release()

        assertTrue(fixture.backend.released)
        assertNull(fixture.extractor.readNextSample())
    }

    private fun createExtractorFixture(
        hasAudio: Boolean,
        audioFormat: MediaFormat = mockMediaFormat(MediaFormat.MIMETYPE_AUDIO_AAC),
        samples: List<FakeAudioSample> = listOf(
            FakeAudioSample(timeUs = 0L),
            FakeAudioSample(timeUs = 33_000L),
            FakeAudioSample(timeUs = 66_000L),
        ),
    ): Fixture {
        val formats = mutableListOf<MediaFormat>()
        formats.add(mockMediaFormat(MediaFormat.MIMETYPE_VIDEO_AVC))
        if (hasAudio) {
            formats.add(audioFormat)
        }
        val backend = FakeExtractorBackend(
            trackFormats = formats,
            samples = samples,
        )
        val extractor = AudioTrackExtractor.createForTest(backend)
        return Fixture(extractor = extractor, backend = backend)
    }

    private fun mockMediaFormat(mime: String): MediaFormat {
        val format = mockk<MediaFormat>(relaxed = true)
        every { format.getString(MediaFormat.KEY_MIME) } returns mime
        return format
    }

    private data class Fixture(
        val extractor: AudioTrackExtractor,
        val backend: FakeExtractorBackend,
    )

    private data class FakeAudioSample(
        val timeUs: Long,
        val flags: Int = 0,
        val size: Int = 16,
    )

    private class FakeExtractorBackend(
        private val trackFormats: List<MediaFormat>,
        private val samples: List<FakeAudioSample>,
    ) : AudioTrackExtractor.ExtractorBackend {

        var lastSeekTimeUs: Long = -1L
            private set

        var released: Boolean = false
            private set

        private var sampleIndex: Int = 0

        override val trackCount: Int
            get() = trackFormats.size

        override fun getTrackFormat(index: Int): MediaFormat = trackFormats[index]

        override fun selectTrack(index: Int) = Unit

        override fun seekTo(timeUs: Long, mode: Int) {
            lastSeekTimeUs = timeUs
            sampleIndex = samples.indexOfFirst { it.timeUs >= timeUs }.let { index ->
                if (index < 0) samples.size else index
            }
        }

        override fun readSampleData(byteBuffer: ByteBuffer, offset: Int): Int {
            if (released) return -1
            val sample = samples.getOrNull(sampleIndex) ?: return -1
            byteBuffer.position(offset)
            repeat(sample.size) {
                byteBuffer.put(0x1)
            }
            return sample.size
        }

        override val sampleTime: Long
            get() = samples.getOrNull(sampleIndex)?.timeUs ?: -1L

        override val sampleFlags: Int
            get() = samples.getOrNull(sampleIndex)?.flags ?: 0

        override fun advance(): Boolean {
            if (sampleIndex >= samples.size) return false
            sampleIndex += 1
            return sampleIndex < samples.size
        }

        override fun release() {
            released = true
        }
    }
}
