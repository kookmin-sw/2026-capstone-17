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
import androidx.media3.exoplayer.ExoPlayer

@Composable
fun rememberExoPlayer(
    uriString: String,
    isPlaying: Boolean,
    onPlaybackEnded: () -> Unit = {}
): ExoPlayer {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    LaunchedEffect(uriString) {
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(uriString)))
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
