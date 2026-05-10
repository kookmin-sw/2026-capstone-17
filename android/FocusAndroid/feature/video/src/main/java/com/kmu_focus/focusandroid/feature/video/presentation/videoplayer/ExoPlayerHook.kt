package com.kmu_focus.focusandroid.feature.video.presentation.videoplayer

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer

@Composable
fun rememberExoPlayer(
    uriString: String,
    isPlaying: Boolean,
    onPlaybackEnded: () -> Unit = {}
): ExoPlayer {
    val context = LocalContext.current
    val isLowLatencyLiveTarget = remember(uriString) { looksLikeHlsUri(uriString) }
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setLoadControl(createLoadControl())
            .setLivePlaybackSpeedControl(createLivePlaybackSpeedControl())
            .build()
    }

    LaunchedEffect(uriString, isLowLatencyLiveTarget) {
        exoPlayer.setMediaItem(
            createMediaItem(
                uriString = uriString,
                isLowLatencyLiveTarget = isLowLatencyLiveTarget,
            )
        )
        exoPlayer.prepare()
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) exoPlayer.play() else exoPlayer.pause()
    }

    // 람다가 바뀌어도 listener를 재등록하지 않도록 rememberUpdatedState 사용
    val currentOnPlaybackEnded = rememberUpdatedState(onPlaybackEnded)

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    currentOnPlaybackEnded.value()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    return exoPlayer
}

private fun createMediaItem(
    uriString: String,
    isLowLatencyLiveTarget: Boolean,
): MediaItem {
    val builder = MediaItem.Builder().setUri(Uri.parse(uriString))
    if (isLowLatencyLiveTarget) {
        builder.setLiveConfiguration(
            MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(LIVE_TARGET_OFFSET_MS)
                .setMinOffsetMs(LIVE_MIN_OFFSET_MS)
                .setMaxOffsetMs(LIVE_MAX_OFFSET_MS)
                .setMinPlaybackSpeed(MIN_LIVE_PLAYBACK_SPEED)
                .setMaxPlaybackSpeed(MAX_LIVE_PLAYBACK_SPEED)
                .build()
        )
    }
    return builder.build()
}

private fun createLoadControl(): DefaultLoadControl {
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            MIN_BUFFER_MS,
            MAX_BUFFER_MS,
            BUFFER_FOR_PLAYBACK_MS,
            BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
        )
        .build()
}

private fun createLivePlaybackSpeedControl(): DefaultLivePlaybackSpeedControl {
    return DefaultLivePlaybackSpeedControl.Builder()
        .setFallbackMinPlaybackSpeed(MIN_LIVE_PLAYBACK_SPEED)
        .setFallbackMaxPlaybackSpeed(MAX_LIVE_PLAYBACK_SPEED)
        .build()
}

private fun looksLikeHlsUri(uriString: String): Boolean {
    val normalized = uriString.lowercase()
    return normalized.endsWith(".m3u8") || normalized.contains(".m3u8?")
}

private const val LIVE_TARGET_OFFSET_MS = 1_500L
private const val LIVE_MIN_OFFSET_MS = 500L
private const val LIVE_MAX_OFFSET_MS = 3_000L
private const val MIN_BUFFER_MS = 1_500
private const val MAX_BUFFER_MS = 4_000
private const val BUFFER_FOR_PLAYBACK_MS = 250
private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 500
private const val MIN_LIVE_PLAYBACK_SPEED = 0.97f
private const val MAX_LIVE_PLAYBACK_SPEED = 1.08f
