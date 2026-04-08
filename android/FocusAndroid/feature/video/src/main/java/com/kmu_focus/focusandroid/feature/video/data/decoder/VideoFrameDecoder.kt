package com.kmu_focus.focusandroid.feature.video.data.decoder

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * 재생 시점의 프레임을 디코딩.
 * getFrameAtTime() 반환 해상도는 기기 구현에 따라 영상 원본과 다를 수 있음.
 */
interface VideoFrameDecoder {
    /**
     * @param positionMs 재생 위치 (밀리초)
     * @return 해당 시점에 가까운 프레임 Bitmap (ARGB_8888), 실패 시 null. 호출 후 호출자가 recycle 권장.
     */
    fun decodeFrameAt(uri: String, positionMs: Long): Bitmap?

    /** 영상 메타데이터 상 width/height (디코더가 실제로 이 크기를 주는지는 별도). */
    fun getVideoDimensions(uri: String): Pair<Int, Int>?
}

class VideoFrameDecoderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : VideoFrameDecoder {

    override fun getVideoDimensions(uri: String): Pair<Int, Int>? {
        val retriever = MediaMetadataRetriever()
        return try {
            when {
                uri.startsWith("content://") || uri.startsWith("file://") ->
                    retriever.setDataSource(context, Uri.parse(uri))
                else ->
                    retriever.setDataSource(uri)
            }
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            Pair(w, h)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    override fun decodeFrameAt(uri: String, positionMs: Long): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            when {
                uri.startsWith("content://") || uri.startsWith("file://") ->
                    retriever.setDataSource(context, Uri.parse(uri))
                else ->
                    retriever.setDataSource(uri)
            }
            val timeUs = positionMs * 1000L
            @Suppress("DEPRECATION")
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
