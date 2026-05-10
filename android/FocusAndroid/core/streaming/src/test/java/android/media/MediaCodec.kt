package android.media

class MediaCodec {
    class BufferInfo {
        @JvmField
        var offset: Int = 0

        @JvmField
        var size: Int = 0

        @JvmField
        var presentationTimeUs: Long = 0

        @JvmField
        var flags: Int = 0

        fun set(
            newOffset: Int,
            newSize: Int,
            newPresentationTimeUs: Long,
            newFlags: Int,
        ) {
            offset = newOffset
            size = newSize
            presentationTimeUs = newPresentationTimeUs
            flags = newFlags
        }
    }
}
