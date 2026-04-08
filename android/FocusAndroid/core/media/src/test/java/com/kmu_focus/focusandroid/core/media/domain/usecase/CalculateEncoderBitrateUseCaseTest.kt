package com.kmu_focus.focusandroid.core.media.domain.usecase

import com.kmu_focus.focusandroid.core.media.domain.entity.EncoderConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateEncoderBitrateUseCaseTest {

    private val useCase = CalculateEncoderBitrateUseCase()

    // ── 원본 비트레이트가 있는 경우: 그대로 사용 ──

    @Test
    fun `원본 비트레이트가 존재하면 그대로 사용한다`() {
        val config = useCase(
            width = 1920,
            height = 1080,
            frameRate = 30,
            sourceBitrate = 5_000_000,
        )

        assertEquals(5_000_000, config.bitrate)
    }

    @Test
    fun `원본 비트레이트가 존재해도 상한을 초과하면 상한으로 클램핑한다`() {
        val config = useCase(
            width = 1920,
            height = 1080,
            frameRate = 30,
            sourceBitrate = 50_000_000,
        )

        assertTrue(config.bitrate <= 20_000_000)
    }

    @Test
    fun `원본 비트레이트가 하한 미만이면 하한으로 클램핑한다`() {
        val config = useCase(
            width = 1920,
            height = 1080,
            frameRate = 30,
            sourceBitrate = 100_000,
        )

        assertTrue(config.bitrate >= 1_000_000)
    }

    // ── 원본 비트레이트가 없는 경우: 해상도 기반 추정 ──

    @Test
    fun `720p 이하는 4Mbps를 반환한다`() {
        val config = useCase(
            width = 1280,
            height = 720,
            frameRate = 30,
            sourceBitrate = null,
        )

        assertEquals(4_000_000, config.bitrate)
    }

    @Test
    fun `1080p는 8Mbps를 반환한다`() {
        val config = useCase(
            width = 1920,
            height = 1080,
            frameRate = 30,
            sourceBitrate = null,
        )

        assertEquals(8_000_000, config.bitrate)
    }

    @Test
    fun `4K는 15Mbps를 반환한다`() {
        val config = useCase(
            width = 3840,
            height = 2160,
            frameRate = 30,
            sourceBitrate = null,
        )

        assertEquals(15_000_000, config.bitrate)
    }

    @Test
    fun `480p 이하도 하한 이상이다`() {
        val config = useCase(
            width = 640,
            height = 480,
            frameRate = 30,
            sourceBitrate = null,
        )

        assertTrue(config.bitrate >= 1_000_000)
    }

    // ── I-Frame 간격 ──

    @Test
    fun `I프레임 간격은 2초를 반환한다`() {
        val config = useCase(
            width = 1920,
            height = 1080,
            frameRate = 30,
            sourceBitrate = null,
        )

        assertEquals(2, config.iFrameIntervalSec)
    }

    // ── 프레임레이트 전달 ──

    @Test
    fun `입력 프레임레이트가 그대로 설정된다`() {
        val config = useCase(
            width = 1920,
            height = 1080,
            frameRate = 24,
            sourceBitrate = null,
        )

        assertEquals(24, config.frameRate)
    }

    // ── EncoderConfig 구조 검증 ──

    @Test
    fun `반환된 EncoderConfig에 모든 필드가 포함된다`() {
        val config = useCase(
            width = 1920,
            height = 1080,
            frameRate = 30,
            sourceBitrate = 6_000_000,
        )

        assertEquals(6_000_000, config.bitrate)
        assertEquals(30, config.frameRate)
        assertEquals(2, config.iFrameIntervalSec)
    }

    // ── 경계값 테스트 ──

    @Test
    fun `720p와 1080p 사이 해상도는 비트레이트가 4~8Mbps 범위에 있다`() {
        val config = useCase(
            width = 1440,
            height = 900,
            frameRate = 30,
            sourceBitrate = null,
        )

        assertTrue(config.bitrate in 4_000_000..8_000_000)
    }

    @Test
    fun `1080p와 4K 사이 해상도는 비트레이트가 8~15Mbps 범위에 있다`() {
        val config = useCase(
            width = 2560,
            height = 1440,
            frameRate = 30,
            sourceBitrate = null,
        )

        assertTrue(config.bitrate in 8_000_000..15_000_000)
    }
}
