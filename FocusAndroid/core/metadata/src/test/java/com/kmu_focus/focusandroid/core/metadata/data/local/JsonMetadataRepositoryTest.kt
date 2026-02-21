package com.kmu_focus.focusandroid.core.metadata.data.local

import com.kmu_focus.focusandroid.core.metadata.domain.entity.BBox
import com.kmu_focus.focusandroid.core.metadata.domain.entity.FaceData
import com.kmu_focus.focusandroid.core.metadata.domain.entity.FrameMetadata
import com.kmu_focus.focusandroid.core.metadata.domain.entity.ThreeDMM
import com.kmu_focus.focusandroid.core.metadata.domain.entity.ThreeDMMFormat
import com.kmu_focus.focusandroid.core.metadata.domain.repository.MetadataRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class JsonMetadataRepositoryTest {

    private lateinit var repository: MetadataRepository
    private lateinit var outputDir: File

    @Before
    fun setup() {
        outputDir = Files.createTempDirectory("metadata_test").toFile()
        repository = JsonMetadataRepository(outputDir)
    }

    @After
    fun tearDown() {
        outputDir.deleteRecursively()
    }

    @Test
    fun `sendFrame 후 close하면 JSON 파일이 생성된다`() = runTest {
        repository.sendFrame(
            FrameMetadata(
                sessionId = "test-session",
                ptsUs = 0L,
                faces = emptyList(),
            )
        )
        repository.close()

        val files = outputDir.listFiles()
        assertNotNull(files)
        assertTrue(files!!.isNotEmpty())
    }

    @Test
    fun `저장된 JSON에 session_id와 pts_us가 포함된다`() = runTest {
        repository.sendFrame(
            FrameMetadata(
                sessionId = "test-session",
                ptsUs = 100000L,
                faces = listOf(
                    FaceData(
                        trackingId = 0,
                        bbox = BBox(100, 50, 200, 200),
                        tdmm = ThreeDMM(
                            format = ThreeDMMFormat.ID80_EXP64_POSE6_V1,
                            modelVersion = "3dmm-v1",
                            coeffs = FloatArray(150) { 0.1f },
                            idDim = 80,
                            expDim = 64,
                            poseDim = 6,
                        ),
                    )
                ),
            )
        )
        repository.close()

        val json = outputDir.listFiles()!!.first().readText()
        assertTrue(json.contains("test-session"))
        assertTrue(json.contains("pts_us"))
        assertTrue(json.contains("tdmm_raw"))
        assertTrue(json.contains("coeffs"))
        assertTrue(json.contains("extra_dim"))
        assertTrue(json.contains("ID80_EXP64_POSE6_V1"))
    }

    @Test
    fun `여러 프레임 전송 후 close하면 모든 프레임이 저장된다`() = runTest {
        repeat(10) { i ->
            repository.sendFrame(
                FrameMetadata(
                    sessionId = "test-session",
                    ptsUs = (i * 33333).toLong(),
                    faces = emptyList(),
                )
            )
        }
        repository.close()

        val json = outputDir.listFiles()!!.first().readText()
        assertTrue(json.contains("299997"))
    }

    @Test
    fun `close 후 sendFrame 호출 시 예외가 발생한다`() = runTest {
        repository.close()

        try {
            repository.sendFrame(
                FrameMetadata(
                    sessionId = "test-session",
                    ptsUs = 0L,
                    faces = emptyList(),
                )
            )
            fail("close 후 sendFrame은 예외가 발생해야 한다")
        } catch (e: IllegalStateException) {
            // 예상된 예외
        }
    }
}
