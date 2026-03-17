package com.kmu_focus.focusandroid.feature.video.data.repository

import com.kmu_focus.focusandroid.core.media.data.local.VideoLocalDataSource
import com.kmu_focus.focusandroid.core.media.data.recorder.AudioTrackExtractor
import com.kmu_focus.focusandroid.core.media.data.recorder.RealTimeRecorder
import com.kmu_focus.focusandroid.feature.video.data.metadata.SourceVideoMetadataReader
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

/**
 * RecordingRepositoryImpl 변경사항 테스트:
 * - startRecording에 sourceUri를 받아 AudioTrackExtractor를 생성하고
 *   RealTimeRecorder에 AudioTrackSource로 전달해야 한다.
 */
class RecordingRepositoryImplTest {

    private val realTimeRecorder = mockk<RealTimeRecorder>(relaxed = true)
    private val videoLocalDataSource = mockk<VideoLocalDataSource>()
    private val audioExtractorFactory = mockk<AudioTrackExtractor.Factory>()
    private val sourceVideoMetadataReader = mockk<SourceVideoMetadataReader>()

    private val repository = RecordingRepositoryImpl(
        realTimeRecorder = realTimeRecorder,
        videoLocalDataSource = videoLocalDataSource,
        audioExtractorFactory = audioExtractorFactory,
        sourceVideoMetadataReader = sourceVideoMetadataReader,
    )

    @Test
    fun `startRecording with sourceUri creates AudioTrackExtractor and passes to recorder`() {
        // Given
        val tempFile = File("/tmp/recording.mp4")
        val sourceUri = "content://media/external/video/1"
        val audioExtractor = mockk<AudioTrackExtractor>(relaxed = true)
        val sourceBitrate = 3_500_000

        every { videoLocalDataSource.createTempOutputFile() } returns tempFile
        every { audioExtractorFactory.create(sourceUri) } returns audioExtractor
        every { sourceVideoMetadataReader.readVideoBitrate(sourceUri) } returns sourceBitrate

        // When
        val result = repository.startRecording(
            width = 1920,
            height = 1080,
            sourceUri = sourceUri,
            onSurfaceReady = { _, _, _ -> },
        )

        // Then: RealTimeRecorder.start에 audioTrackSource가 전달되어야 함
        verify {
            realTimeRecorder.start(
                width = 1920,
                height = 1080,
                outputFile = tempFile,
                bitRate = sourceBitrate,
                frameRate = any(),
                audioTrackSource = audioExtractor,
                audioStartPositionUs = any(),
                onInputSurfaceReady = any(),
            )
        }
        assertNotNull(result)
    }

    @Test
    fun `startRecording without sourceUri passes null audioTrackSource`() {
        // Given: sourceUri가 null이면 기존 video-only 동작
        val tempFile = File("/tmp/recording.mp4")
        every { videoLocalDataSource.createTempOutputFile() } returns tempFile

        // When
        repository.startRecording(
            width = 1920,
            height = 1080,
            sourceUri = null,
            onSurfaceReady = { _, _, _ -> },
        )

        // Then
        verify {
            realTimeRecorder.start(
                width = 1920,
                height = 1080,
                outputFile = tempFile,
                bitRate = null,
                frameRate = any(),
                audioTrackSource = null,
                audioStartPositionUs = any(),
                onInputSurfaceReady = any(),
            )
        }
        verify(exactly = 0) { sourceVideoMetadataReader.readVideoBitrate(any()) }
    }

    @Test
    fun `stopRecording delegates to realTimeRecorder`() {
        // When
        repository.stopRecording()

        // Then
        verify { realTimeRecorder.stop() }
    }
}
