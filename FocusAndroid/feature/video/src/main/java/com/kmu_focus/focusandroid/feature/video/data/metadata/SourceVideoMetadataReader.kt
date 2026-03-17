package com.kmu_focus.focusandroid.feature.video.data.metadata

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SourceVideoMetadataReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun readVideoBitrate(sourceUri: String): Int? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, Uri.parse(sourceUri), null)
            val videoTrackIndex = findTrack(extractor, VIDEO_MIME_PREFIX)
            if (videoTrackIndex < 0) {
                null
            } else {
                extractor.getTrackFormat(videoTrackIndex)
                    .getIntegerOrNull(MediaFormat.KEY_BIT_RATE)
                    ?.takeIf { it > 0 }
            }
        } catch (error: Exception) {
            Log.w(TAG, "원본 비디오 비트레이트 읽기 실패: $sourceUri", error)
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun findTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (trackIndex in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(trackIndex).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(mimePrefix)) return trackIndex
        }
        return -1
    }

    private fun MediaFormat.getIntegerOrNull(key: String): Int? {
        return try {
            if (containsKey(key)) getInteger(key) else null
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        private const val TAG = "SourceVideoMetadata"
        private const val VIDEO_MIME_PREFIX = "video/"
    }
}
