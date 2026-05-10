package com.kmu_focus.focusandroid.core.grpc.data.repository

import com.kmu_focus.focusandroid.core.grpc.data.remote.FaceMetadataStreamManager
import com.kmu_focus.focusandroid.core.metadata.domain.entity.BBox
import com.kmu_focus.focusandroid.core.metadata.domain.entity.FaceData
import com.kmu_focus.focusandroid.core.metadata.domain.entity.FrameMetadata
import com.kmu_focus.focusandroid.core.metadata.domain.entity.ThreeDMM
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class GrpcMetadataRepositoryImplTest {

    private lateinit var streamManager: FaceMetadataStreamManager
    private lateinit var repository: GrpcMetadataRepositoryImpl

    private val sampleMetadata = FrameMetadata(
        sessionId = "broadcast-123",
        ptsUs = 133_333L,
        faces = listOf(
            FaceData(
                trackingId = 0,
                bbox = BBox(x = 100, y = 200, width = 50, height = 60),
                tdmm = ThreeDMM(coeffs = FloatArray(265) { it * 0.01f }),
            ),
        ),
    )

    @Before
    fun setup() {
        streamManager = mockk(relaxed = true)
        repository = GrpcMetadataRepositoryImpl(streamManager)
    }

    @Test
    fun `sendFrame 호출 시 streamManager에 proto 메시지를 전달한다`() = runTest {
        repository.sendFrame(sampleMetadata)

        verify(exactly = 1) { streamManager.sendFrame(any()) }
    }

    @Test
    fun `sendFrame에서 sessionId가 정확히 전달된다`() = runTest {
        repository.sendFrame(sampleMetadata)

        verify {
            streamManager.sendFrame(match { it.sessionId == "broadcast-123" })
        }
    }

    @Test
    fun `sendFrame에서 ptsUs가 정확히 전달된다`() = runTest {
        repository.sendFrame(sampleMetadata)

        verify {
            streamManager.sendFrame(match { it.ptsUs == 133_333L })
        }
    }

    @Test
    fun `sendFrame에서 얼굴 데이터가 매핑되어 전달된다`() = runTest {
        repository.sendFrame(sampleMetadata)

        verify {
            streamManager.sendFrame(match { it.facesCount == 1 })
        }
    }

    @Test
    fun `close 호출 시 streamManager의 complete를 호출한다`() = runTest {
        repository.close()

        verify(exactly = 1) { streamManager.complete() }
    }

    @Test
    fun `여러 프레임을 순차적으로 전송할 수 있다`() = runTest {
        val frame1 = sampleMetadata.copy(ptsUs = 100_000L)
        val frame2 = sampleMetadata.copy(ptsUs = 133_333L)
        val frame3 = sampleMetadata.copy(ptsUs = 166_666L)

        repository.sendFrame(frame1)
        repository.sendFrame(frame2)
        repository.sendFrame(frame3)

        verify(exactly = 3) { streamManager.sendFrame(any()) }
    }

    @Test
    fun `빈 faces 프레임도 전송된다`() = runTest {
        val emptyFrame = FrameMetadata(
            sessionId = "broadcast-123",
            ptsUs = 100_000L,
            faces = emptyList(),
        )

        repository.sendFrame(emptyFrame)

        verify {
            streamManager.sendFrame(match { it.facesCount == 0 })
        }
    }

    @Test
    fun `streamManager 전송 실패 시 예외가 전파된다`() = runTest {
        every { streamManager.sendFrame(any()) } throws RuntimeException("gRPC 전송 실패")

        val result = runCatching { repository.sendFrame(sampleMetadata) }

        assert(result.isFailure)
    }
}
