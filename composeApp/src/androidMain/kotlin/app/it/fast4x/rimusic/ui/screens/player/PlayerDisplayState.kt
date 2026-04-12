package app.it.fast4x.rimusic.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.C  // ← ADD THIS IMPORT
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import app.it.fast4x.rimusic.service.modern.CrossfadeUiState
import app.it.fast4x.rimusic.service.modern.PlayerServiceModern
import app.it.fast4x.rimusic.utils.DisposableListener
import app.it.fast4x.rimusic.utils.PlaybackProgressState
import app.it.fast4x.rimusic.utils.playbackProgressState

@Stable
data class PlayerDisplayState(
    val crossfadeUiState: CrossfadeUiState,
    val mediaItem: MediaItem?,
    val shouldBePlaying: Boolean,
    val isBuffering: Boolean,
    val position: Long,
    val duration: Long,
    val bufferedPosition: Long,
)

private fun isRecoverableError(error: PlaybackException?): Boolean {
    if (error == null) return true
    val recoverableCodes = listOf(
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    )
    return error.errorCode in recoverableCodes
}

@Composable
fun rememberDisplayedPlayerState(
    binder: PlayerServiceModern.Binder,
): PlayerDisplayState {
    val crossfadeUiState by binder.crossfadeUiState.collectAsState()
    val playbackProgress by binder.player.playbackProgressState()
    var currentMediaItem by remember {
        mutableStateOf(binder.displayedMediaItem ?: binder.player.currentMediaItem)
    }
    var playbackStateValue by remember {
        mutableIntStateOf(binder.player.playbackState)
    }
    var playWhenReadyState by remember {
        mutableStateOf(binder.player.playWhenReady)
    }
    var isPlaying by remember {
        mutableStateOf(binder.player.isPlaying)
    }
    var playerError by remember {
        mutableStateOf<PlaybackException?>(binder.player.playerError)
    }

    binder.player.DisposableListener {
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentMediaItem = binder.displayedMediaItem ?: mediaItem
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                playbackStateValue = playbackState
                playerError = binder.player.playerError
                isPlaying = binder.player.isPlaying
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playWhenReadyState = playWhenReady
                isPlaying = binder.player.isPlaying
            }

            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }

            override fun onPlayerError(error: PlaybackException) {
                playerError = error
            }
        }
    }

    val mediaItem by remember(
        binder,
        crossfadeUiState,
        currentMediaItem,
    ) {
        derivedStateOf {
            if (crossfadeUiState.isEnabled) {
                crossfadeUiState.displayMediaItem
                    ?: binder.displayedMediaItem
                    ?: currentMediaItem
            } else {
                binder.displayedMediaItem ?: currentMediaItem
            }
        }
    }

    val shouldBePlaying by remember(
        binder,
        crossfadeUiState,
        playWhenReadyState,
        isPlaying,
        playerError,
        binder.sessionPlayer.playWhenReady,
        binder.sessionPlayer.isPlaying,
    ) {
        derivedStateOf {
            // Don't block playback just because there was an error
            // Let the error handler decide what to do
            (binder.sessionPlayer.playWhenReady ||
                binder.sessionPlayer.isPlaying ||
                (playWhenReadyState && crossfadeUiState.isActive) ||
                isPlaying) &&
                // Only block if it's a fatal error that requires user action
                (playerError == null || isRecoverableError(playerError))
        }
    }

    val isBuffering by remember(playbackStateValue) {
        derivedStateOf { playbackStateValue == Player.STATE_BUFFERING }
    }

    val displayedProgress by remember(crossfadeUiState, playbackProgress) {
        derivedStateOf {
            fun Long.safeMs(): Long = if (this == C.TIME_UNSET || this < 0L) 0L else this

            if (crossfadeUiState.isEnabled && crossfadeUiState.displayDuration > 0L) {
                val safeDuration = crossfadeUiState.displayDuration.safeMs().coerceAtLeast(1L)
                val safePosition = crossfadeUiState.displayPosition.safeMs().coerceIn(0L, safeDuration)
                PlaybackProgressState(
                    position = safePosition,
                    duration = safeDuration,
                    bufferedPosition = safePosition,
                )
            } else {
                val rawDuration = playbackProgress.duration.safeMs()
                val safeDuration = rawDuration.coerceAtLeast(1L)
                val safePosition = playbackProgress.position.safeMs().coerceIn(0L, safeDuration)
                val safeBuffered = playbackProgress.bufferedPosition.safeMs().coerceIn(safePosition, safeDuration)
                PlaybackProgressState(
                    position = safePosition,
                    duration = safeDuration,
                    bufferedPosition = safeBuffered,
                )
            }
        }
    }

    return remember(
        crossfadeUiState,
        mediaItem,
        shouldBePlaying,
        isBuffering,
        displayedProgress,
    ) {
        PlayerDisplayState(
            crossfadeUiState = crossfadeUiState,
            mediaItem = mediaItem,
            shouldBePlaying = shouldBePlaying,
            isBuffering = isBuffering,
            position = displayedProgress.position,
            duration = displayedProgress.duration,
            bufferedPosition = displayedProgress.bufferedPosition,
        )
    }
}
