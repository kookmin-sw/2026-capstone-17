package com.kmu_focus.focusandroid.core.media.domain.usecase

import com.kmu_focus.focusandroid.core.media.domain.entity.EncoderConfig
import javax.inject.Inject
import kotlin.math.roundToInt

class CalculateEncoderBitrateUseCase @Inject constructor() {

    operator fun invoke(
        width: Int,
        height: Int,
        frameRate: Int,
        sourceBitrate: Int?,
    ): EncoderConfig {
        val resolvedBitrate = sourceBitrate?.coerceIn(MIN_BITRATE, MAX_BITRATE)
            ?: estimateBitrate(width = width, height = height)

        return EncoderConfig(
            bitrate = resolvedBitrate,
            frameRate = frameRate.coerceAtLeast(MIN_FRAME_RATE),
            iFrameIntervalSec = DEFAULT_I_FRAME_INTERVAL_SEC,
        )
    }

    private fun estimateBitrate(width: Int, height: Int): Int {
        val pixelCount = width.toLong().coerceAtLeast(1L) * height.toLong().coerceAtLeast(1L)
        val estimatedBitrate = when {
            pixelCount <= PIXELS_720P -> BITRATE_720P
            pixelCount <= PIXELS_1080P -> interpolate(
                value = pixelCount,
                startValue = PIXELS_720P,
                endValue = PIXELS_1080P,
                startBitrate = BITRATE_720P,
                endBitrate = BITRATE_1080P,
            )

            pixelCount <= PIXELS_4K -> interpolate(
                value = pixelCount,
                startValue = PIXELS_1080P,
                endValue = PIXELS_4K,
                startBitrate = BITRATE_1080P,
                endBitrate = BITRATE_4K,
            )

            else -> BITRATE_4K
        }

        return estimatedBitrate.coerceIn(MIN_BITRATE, MAX_BITRATE)
    }

    private fun interpolate(
        value: Long,
        startValue: Long,
        endValue: Long,
        startBitrate: Int,
        endBitrate: Int,
    ): Int {
        if (endValue <= startValue) return endBitrate
        val ratio = (value - startValue).toDouble() / (endValue - startValue).toDouble()
        return (startBitrate + (endBitrate - startBitrate) * ratio).roundToInt()
    }

    private companion object {
        private const val MIN_BITRATE = 1_000_000
        private const val MAX_BITRATE = 20_000_000
        private const val MIN_FRAME_RATE = 1
        private const val DEFAULT_I_FRAME_INTERVAL_SEC = 2

        private const val BITRATE_720P = 4_000_000
        private const val BITRATE_1080P = 8_000_000
        private const val BITRATE_4K = 15_000_000

        private const val PIXELS_720P = 1280L * 720L
        private const val PIXELS_1080P = 1920L * 1080L
        private const val PIXELS_4K = 3840L * 2160L
    }
}
