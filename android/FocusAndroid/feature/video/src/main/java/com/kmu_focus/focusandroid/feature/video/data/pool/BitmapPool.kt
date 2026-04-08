package com.kmu_focus.focusandroid.feature.video.data.pool

import android.graphics.Bitmap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 얼굴 crop용 Bitmap 재사용. extractLabelsFromOriginalFrame에서 GC 스파이크 방지.
 */
@Singleton
class BitmapPool @Inject constructor() {

    private val pool = ConcurrentLinkedQueue<Bitmap>()
    private val maxPoolSize = 8

    fun acquire(width: Int, height: Int): Bitmap {
        var bitmap = pool.poll()
        if (bitmap != null && (bitmap.width < width || bitmap.height < height)) {
            if (!bitmap.isRecycled) bitmap.recycle()
            bitmap = null
        }
        return bitmap ?: Bitmap.createBitmap(
            width.coerceAtLeast(1),
            height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
    }

    fun release(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return
        if (pool.size < maxPoolSize) {
            bitmap.eraseColor(0)
            pool.offer(bitmap)
        } else {
            bitmap.recycle()
        }
    }

    fun clear() {
        while (true) {
            val b = pool.poll() ?: break
            if (!b.isRecycled) b.recycle()
        }
    }
}
