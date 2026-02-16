package com.kmu_focus.focusandroid.feature.video.presentation.videoplayer

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.kmu_focus.focusandroid.core.ai.domain.entity.DetectedFace

@Composable
fun FaceTouchOverlay(
    faces: List<DetectedFace>,
    trackingIds: List<Int>,
    frameWidth: Int,
    frameHeight: Int,
    onFaceTapped: (trackId: Int, faceIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (frameWidth <= 0 || frameHeight <= 0 || faces.isEmpty()) return

    val currentFaces by rememberUpdatedState(faces)
    val currentTrackingIds by rememberUpdatedState(trackingIds)
    val currentFrameWidth by rememberUpdatedState(frameWidth)
    val currentFrameHeight by rememberUpdatedState(frameHeight)
    val currentOnFaceTapped by rememberUpdatedState(onFaceTapped)

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { tapOffset ->
                val hit = findFaceHitAtTap(
                    tapOffset = tapOffset,
                    canvasWidth = size.width.toFloat(),
                    canvasHeight = size.height.toFloat(),
                    faces = currentFaces,
                    trackingIds = currentTrackingIds,
                    frameWidth = currentFrameWidth,
                    frameHeight = currentFrameHeight,
                ) ?: return@detectTapGestures

                currentOnFaceTapped(hit.trackId, hit.faceIndex)
            }
        }
    )
}

private fun findFaceHitAtTap(
    tapOffset: Offset,
    canvasWidth: Float,
    canvasHeight: Float,
    faces: List<DetectedFace>,
    trackingIds: List<Int>,
    frameWidth: Int,
    frameHeight: Int,
): FaceHit? {
    if (canvasWidth <= 0f || canvasHeight <= 0f) return null

    val frameX = tapOffset.x / canvasWidth * frameWidth
    val frameY = tapOffset.y / canvasHeight * frameHeight

    for (index in faces.indices.reversed()) {
        val face = faces[index]
        val left = face.x.toFloat()
        val top = face.y.toFloat()
        val right = left + face.width
        val bottom = top + face.height
        val isHit = frameX in left..right && frameY in top..bottom
        if (isHit) {
            return FaceHit(
                trackId = trackingIds.getOrNull(index) ?: index,
                faceIndex = index,
            )
        }
    }
    return null
}

private data class FaceHit(
    val trackId: Int,
    val faceIndex: Int,
)
