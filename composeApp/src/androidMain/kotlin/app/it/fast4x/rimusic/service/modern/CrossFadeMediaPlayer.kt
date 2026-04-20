package app.it.fast4x.rimusic.service.modern

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.utils.mediaItems
import app.it.fast4x.rimusic.utils.isPlayable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.lang.reflect.Method
import kotlin.math.pow

data class CrossfadeUiState(
    val isEnabled: Boolean = false,
    val isActive: Boolean = false,
    val isHighlightActive: Boolean = false,
    val progress: Float = 0f,
    val displayPosition: Long = 0L,
    val displayDuration: Long = 0L,
    val displayMediaItem: MediaItem? = null,
    val incomingMediaItem: MediaItem? = null,
)

@UnstableApi
class CrossFadeManager(
    private var currentPlayer: ExoPlayer,
    private var nextPlayer: ExoPlayer,
    private val targetVolumeProvider: () -> Float,
    private val onPlayersSwapped: (ExoPlayer, ExoPlayer) -> Unit,
    private val onCrossfadeActiveChanged: (Boolean) -> Unit = {},
) {
    private val handler = Handler(Looper.getMainLooper())
    private val monitorIntervalMs = 100L
    private val readyTimeoutMs = 3_500L
    private val activeBufferingTimeoutMs = 8_000L
    private val crossfadeCompletionGraceMs = 400L
    private val earlyStartLeadInMs = 0L
    private val incomingStartVolumeRatio = 0.05f
    private val outgoingHoldPortion = 0f
    private val minimumCrossfadeDurationMs = 250L
    private val preloadPaddingMs = 400L

    private var enabled = false
    private var crossfadeDurationMs = 0L
    private var preloadedIndex = C.INDEX_UNSET
    private var preloadedMediaId: String? = null
    private var waitingReadySinceMs = 0L
    private var skippedMediaId: String? = null
    private var isCrossfading = false
    private var activeBufferingSinceMs = 0L
    private var crossfadeStartedAtMs = 0L
    private var activeCrossfadeDurationMs = 0L
    private var outgoingMediaId: String? = null
    private var outgoingExpectedEndAtMs = 0L
    private var baseVolume = 1f
    private var isMonitoring = false
    private var lastReportedCrossfadeActive = false
    private var pauseAtEndMethod: Method? = null
    private val _uiState = MutableStateFlow(CrossfadeUiState())
    val uiState: StateFlow<CrossfadeUiState> = _uiState
    var onDisplayItemChanged: ((MediaItem?) -> Unit)? = null

    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoring) return
            tick()
            handler.postDelayed(this, monitorIntervalMs)
        }
    }

    init {
        nextPlayer.repeatMode = Player.REPEAT_MODE_OFF
        nextPlayer.playWhenReady = false
        nextPlayer.volume = 0f
        pauseAtEndMethod = runCatching {
            ExoPlayer::class.java.getMethod("setPauseAtEndOfMediaItems", Boolean::class.javaPrimitiveType)
        }.getOrNull()
    }

    fun updateConfig(
        enabled: Boolean,
        crossfadeDurationMs: Long,
    ) {
        this.enabled = enabled
        this.crossfadeDurationMs = crossfadeDurationMs.coerceAtLeast(0L)

        if (!enabled || this.crossfadeDurationMs <= 0L) {
            cancelCrossfade()
            stopMonitoring()
        } else {
            startMonitoring()
        }
    }

    fun onPrimaryTimelineChanged() {
        val preloadedIndex = preloadedIndex
        if (preloadedIndex == C.INDEX_UNSET || preloadedIndex >= currentPlayer.mediaItemCount) {
            resetNextPlayer()
            return
        }

        val expectedMediaId = runCatching {
            currentPlayer.getMediaItemAt(preloadedIndex).mediaId
        }.getOrNull()
        if (expectedMediaId != preloadedMediaId) {
            resetNextPlayer()
        }
    }

    fun onPrimaryMediaItemTransition(mediaItem: MediaItem?) {
        skippedMediaId = null
        waitingReadySinceMs = 0L
        baseVolume = targetVolumeProvider()

        if (mediaItem == null) {
            resetNextPlayer()
            updateUiState()
            return
        }

        if (mediaItem.mediaId != preloadedMediaId && !isCrossfading) {
            resetNextPlayer()
        }

        updateUiState()
    }

    fun onPrimaryIsPlayingChanged(isPlaying: Boolean) {
        if (!isCrossfading) return

        if (isPlaying) {
            if (nextPlayer.playbackState == Player.STATE_READY) {
                nextPlayer.play()
            }
        } else {
            nextPlayer.pause()
        }
    }

    fun onPrimaryPlayWhenReadyChanged(playWhenReady: Boolean) {
        if (!isCrossfading) return

        if (playWhenReady) {
            if (nextPlayer.playbackState == Player.STATE_READY) {
                nextPlayer.play()
            }
        } else {
            nextPlayer.pause()
        }
    }

    fun pauseForUiInteraction() {
        nextPlayer.pause()
    }

    fun playForUiInteraction() {
        if (isCrossfading && nextPlayer.playbackState == Player.STATE_READY) {
            nextPlayer.play()
        }
    }

    fun onPrimaryPositionDiscontinuity(reason: Int) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SKIP) {
            cancelCrossfade()
            if (currentPlayer.playWhenReady) {
                if (currentPlayer.playbackState == Player.STATE_IDLE) {
                    currentPlayer.prepare()
                }
                currentPlayer.play()
            }
        }
    }

    fun recoverFromSecondaryError(): Boolean {
        if (!isCrossfading) return false
        return runCatching {
            isCrossfading = false
            notifyCrossfadeActive(false)
            waitingReadySinceMs = 0L
            activeBufferingSinceMs = 0L
            activeCrossfadeDurationMs = 0L
            outgoingMediaId = null
            outgoingExpectedEndAtMs = 0L
            skippedMediaId = currentPlayer.currentMediaItem?.mediaId
            setPauseAtEndOfMediaItems(currentPlayer, false)
            currentPlayer.volume = baseVolume.coerceAtLeast(targetVolumeProvider())
            val currentState = runCatching { currentPlayer.playbackState }.getOrDefault(Player.STATE_IDLE)
            if (currentState == Player.STATE_IDLE) {
                currentPlayer.prepare()
            }
            if (currentPlayer.playWhenReady && !currentPlayer.isPlaying) {
                currentPlayer.play()
            }
            resetNextPlayer()
            updateUiState()
            true
        }.getOrElse { error ->
            Timber.e(error, "CrossFadeManager recoverFromSecondaryError crashed, forcing full crossfade reset")
            runCatching { cancelCrossfade() }
                .onFailure { Timber.e(it, "CrossFadeManager cancelCrossfade also failed during recovery") }
            false
        }
    }

    fun release() {
        stopMonitoring()
        notifyCrossfadeActive(false)
        nextPlayer.release()
    }

    fun shouldHoldWakeLock(): Boolean {
        if (!enabled) return false
        if (isCrossfading) return true
        if (!currentPlayer.playWhenReady) return false
        return waitingReadySinceMs > 0L ||
            (preloadedIndex != C.INDEX_UNSET && nextPlayer.playbackState == Player.STATE_BUFFERING) ||
            (preloadedIndex != C.INDEX_UNSET && nextPlayer.playbackState == Player.STATE_READY)
    }

    fun reportedPlaybackState(): Int? {
        if (!enabled) return null
        return when {
            isCrossfading && nextPlayer.playbackState == Player.STATE_BUFFERING -> Player.STATE_BUFFERING
            isCrossfading && nextPlayer.playbackState == Player.STATE_IDLE -> Player.STATE_BUFFERING
            isCrossfading && (nextPlayer.playbackState == Player.STATE_READY || nextPlayer.isPlaying) -> Player.STATE_READY
            waitingReadySinceMs > 0L && currentPlayer.playWhenReady -> Player.STATE_BUFFERING
            preloadedIndex != C.INDEX_UNSET &&
                currentPlayer.playWhenReady &&
                nextPlayer.playbackState == Player.STATE_BUFFERING -> Player.STATE_BUFFERING
            else -> null
        }
    }

    fun reportedIsPlaying(): Boolean? {
        val reportedState = reportedPlaybackState() ?: return null
        return when (reportedState) {
            Player.STATE_READY -> currentPlayer.playWhenReady && (currentPlayer.isPlaying || nextPlayer.isPlaying || isCrossfading)
            Player.STATE_BUFFERING -> false
            else -> null
        }
    }

    fun isTransitioningPlayback(): Boolean =
        enabled && (isCrossfading || waitingReadySinceMs > 0L || preloadedIndex != C.INDEX_UNSET)

    private fun tick() {
        val currentMediaId = currentPlayer.currentMediaItem?.mediaId
        if (currentMediaId == null) {
            if (!isCrossfading) {
                resetNextPlayer()
            }
            updateUiState()
            return
        }

        if (!enabled || crossfadeDurationMs <= 0L || currentPlayer.mediaItemCount < 2) {
            cancelCrossfade()
            return
        }

        if (isCrossfading) {
            updateCrossfade()
            return
        }

        updateUiState()

        val nextIndex = currentPlayer.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) {
            resetNextPlayer()
            return
        }

        val duration = currentPlayer.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return

        val currentPosition = currentPlayer.currentPosition.coerceAtLeast(0L)
        val remaining = (duration - currentPosition).coerceAtLeast(0L)
        val preloadLeadInMs = (crossfadeDurationMs + preloadPaddingMs).coerceAtLeast(1_000L)
        val preloadStartPosition = (duration - preloadLeadInMs).coerceAtLeast(0L)

        val shouldPreload = currentPosition >= preloadStartPosition
        if (shouldPreload) {
            ensureSecondaryPrepared(nextIndex)
        }

        val effectiveCrossfadeWindowMs = crossfadeDurationMs + earlyStartLeadInMs
        if (
            currentPosition < preloadStartPosition ||
            remaining > effectiveCrossfadeWindowMs ||
            skippedMediaId == currentMediaId
        ) {
            return
        }

        val nextReadyForCrossfade = ensureSecondaryPrepared(nextIndex)

        if (nextReadyForCrossfade && nextPlayer.playbackState == Player.STATE_READY) {
            startCrossfade(effectiveCrossfadeWindowMs)
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (waitingReadySinceMs == 0L) {
            waitingReadySinceMs = now
        }

        if (now - waitingReadySinceMs >= readyTimeoutMs) {
            skippedMediaId = currentMediaId
            waitingReadySinceMs = 0L
        }
    }

    private fun ensureSecondaryPrepared(nextIndex: Int): Boolean {
        if (!enabled || crossfadeDurationMs <= 0L) return false
        if (nextIndex == C.INDEX_UNSET || nextIndex >= currentPlayer.mediaItemCount) {
            resetNextPlayer()
            return false
        }

        val mediaItem = currentPlayer.getMediaItemAt(nextIndex)
        if (!mediaItem.isPlayable()) {
            skippedMediaId = currentPlayer.currentMediaItem?.mediaId
            resetNextPlayer()
            return false
        }
        val nextMediaId = mediaItem.mediaId.trim()
        if (nextMediaId.isBlank()) {
            skippedMediaId = currentPlayer.currentMediaItem?.mediaId
            resetNextPlayer()
            return false
        }
        if (preloadedIndex == nextIndex && preloadedMediaId == nextMediaId) return true

        isCrossfading = false
        waitingReadySinceMs = 0L
        activeBufferingSinceMs = 0L

        val queue = buildQueueSnapshot(currentPlayer)
        return runCatching {
            nextPlayer.stop()
            nextPlayer.clearMediaItems()
            nextPlayer.setMediaItems(queue, nextIndex, 0L)
            nextPlayer.prepare()
            nextPlayer.playWhenReady = false
            nextPlayer.volume = 0f
            nextPlayer.playbackParameters = currentPlayer.playbackParameters
            nextPlayer.repeatMode = currentPlayer.repeatMode
            nextPlayer.shuffleModeEnabled = currentPlayer.shuffleModeEnabled

            preloadedIndex = nextIndex
            preloadedMediaId = nextMediaId
            true
        }.getOrElse {
            skippedMediaId = currentPlayer.currentMediaItem?.mediaId
            resetNextPlayer()
            false
        }
    }

    private fun startCrossfade(durationMs: Long) {
        if (isCrossfading) return
        if (preloadedIndex == C.INDEX_UNSET || preloadedMediaId.isNullOrBlank()) return
        if (nextPlayer.playbackState == Player.STATE_IDLE) return

        waitingReadySinceMs = 0L
        isCrossfading = true
        notifyCrossfadeActive(true)
        crossfadeStartedAtMs = SystemClock.elapsedRealtime()
        activeBufferingSinceMs = 0L
        val remainingDurationMs = currentPlayer.duration
            .takeIf { it != C.TIME_UNSET && it > 0L }
            ?.let { duration ->
                (duration - currentPlayer.currentPosition.coerceAtLeast(0L)).coerceAtLeast(0L)
            }
            ?: durationMs
        activeCrossfadeDurationMs = durationMs
            .coerceAtMost(remainingDurationMs.coerceAtLeast(minimumCrossfadeDurationMs))
            .coerceAtLeast(minimumCrossfadeDurationMs)
        outgoingMediaId = currentPlayer.currentMediaItem?.mediaId
        outgoingExpectedEndAtMs = currentPlayer.duration
            .takeIf { it != C.TIME_UNSET && it > 0L }
            ?.let { duration ->
                SystemClock.elapsedRealtime() + (duration - currentPlayer.currentPosition.coerceAtLeast(0L))
                    .coerceAtLeast(crossfadeCompletionGraceMs)
            } ?: 0L
        baseVolume = targetVolumeProvider()
        currentPlayer.volume = baseVolume
        setPauseAtEndOfMediaItems(currentPlayer, true)

        runCatching {
            nextPlayer.seekTo(preloadedIndex, 0L)
        }.onFailure {
            recoverFromSecondaryError()
            return
        }
        nextPlayer.volume = 0f
        nextPlayer.playbackParameters = currentPlayer.playbackParameters

        if (currentPlayer.playWhenReady) {
            nextPlayer.play()
        } else {
            nextPlayer.pause()
        }
    }

    private fun updateCrossfade() {
        if (!isCrossfading) return

        if (!currentPlayer.playWhenReady) {
            activeBufferingSinceMs = 0L
            nextPlayer.pause()
            return
        }

        if (!nextPlayer.isPlaying && nextPlayer.playbackState == Player.STATE_READY) {
            nextPlayer.play()
        }

        val now = SystemClock.elapsedRealtime()
        val nextIsUnavailable = nextPlayer.playbackState == Player.STATE_IDLE || nextPlayer.playbackState == Player.STATE_ENDED
        val nextIsBuffering = nextPlayer.playbackState == Player.STATE_BUFFERING ||
            (nextPlayer.playbackState == Player.STATE_READY && !nextPlayer.isPlaying && currentPlayer.playWhenReady)

        if (nextIsUnavailable) {
            recoverFromSecondaryError()
            return
        }

        if (nextIsBuffering) {
            if (activeBufferingSinceMs == 0L) {
                activeBufferingSinceMs = now
            } else if (now - activeBufferingSinceMs >= activeBufferingTimeoutMs) {
                recoverFromSecondaryError()
                return
            }
        } else {
            activeBufferingSinceMs = 0L
        }

        val elapsed = now - crossfadeStartedAtMs
        val fraction = (elapsed.toFloat() / activeCrossfadeDurationMs.toFloat()).coerceIn(0f, 1f)
        val normalizedProgress = ((fraction - outgoingHoldPortion) / (1f - outgoingHoldPortion))
            .coerceIn(0f, 1f)
        val smoothProgress = normalizedProgress * normalizedProgress * (3f - 2f * normalizedProgress)
        val fadeOut = (1f - smoothProgress).pow(1.55f)
        val fadeIn = incomingStartVolumeRatio +
            ((1f - incomingStartVolumeRatio) * smoothProgress.pow(1.15f))
        val currentDuration = currentPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        val currentRemaining = currentDuration?.let { duration ->
            (duration - currentPlayer.currentPosition.coerceAtLeast(0L)).coerceAtLeast(0L)
        }
        val outgoingReachedExpectedEnd =
            outgoingExpectedEndAtMs > 0L &&
                SystemClock.elapsedRealtime() + crossfadeCompletionGraceMs >= outgoingExpectedEndAtMs

        currentPlayer.volume = (baseVolume * fadeOut).coerceIn(0f, baseVolume)
        nextPlayer.volume = (baseVolume * fadeIn).coerceIn(0f, baseVolume)
        updateUiState(elapsed)

        val incomingReady = nextPlayer.playbackState == Player.STATE_READY || nextPlayer.isPlaying
        if (
            incomingReady && (
                outgoingReachedExpectedEnd ||
                    (
                        currentRemaining != null &&
                            currentRemaining <= crossfadeCompletionGraceMs &&
                            currentPlayer.currentMediaItem?.mediaId == outgoingMediaId
                        ) ||
                    currentPlayer.playbackState == Player.STATE_ENDED
                )
        ) {
            completeCrossfade()
        }
    }

    private fun completeCrossfade() {
        val outgoingPlayer = currentPlayer
        val incomingPlayer = nextPlayer
        val incomingState = runCatching { incomingPlayer.playbackState }.getOrDefault(Player.STATE_IDLE)
        if (incomingState == Player.STATE_IDLE || incomingState == Player.STATE_ENDED) {
            Timber.w(
                "CrossFadeManager completeCrossfade aborted because incoming player is not playable state=%s mediaId=%s",
                incomingState,
                incomingPlayer.currentMediaItem?.mediaId
            )
            recoverFromSecondaryError()
            return
        }
        val shouldKeepPlaying = outgoingPlayer.playWhenReady || outgoingPlayer.isPlaying
        isCrossfading = false
        notifyCrossfadeActive(false)
        activeBufferingSinceMs = 0L
        activeCrossfadeDurationMs = 0L
        outgoingMediaId = null
        outgoingExpectedEndAtMs = 0L

        runCatching {
            incomingPlayer.volume = baseVolume
            incomingPlayer.repeatMode = outgoingPlayer.repeatMode
            incomingPlayer.shuffleModeEnabled = outgoingPlayer.shuffleModeEnabled
            incomingPlayer.playbackParameters = outgoingPlayer.playbackParameters
            incomingPlayer.playWhenReady = shouldKeepPlaying

            currentPlayer = incomingPlayer
            nextPlayer = outgoingPlayer
            setPauseAtEndOfMediaItems(currentPlayer, false)
            if (currentPlayer.playbackState == Player.STATE_IDLE) {
                currentPlayer.prepare()
            }
            if (shouldKeepPlaying && !currentPlayer.isPlaying) {
                currentPlayer.play()
            }

            nextPlayer.pause()
            nextPlayer.stop()
            nextPlayer.clearMediaItems()
            nextPlayer.volume = 0f
            setPauseAtEndOfMediaItems(nextPlayer, false)

            waitingReadySinceMs = 0L
            skippedMediaId = null
            preloadedIndex = C.INDEX_UNSET
            preloadedMediaId = null
            onPlayersSwapped(currentPlayer, nextPlayer)
            updateUiState()
        }.onFailure { error ->
            Timber.e(error, "CrossFadeManager completeCrossfade failed, falling back to recovery")
            recoverFromSecondaryError()
        }
    }

    private fun cancelCrossfade() {
        val shouldRestoreCurrentVolume = isCrossfading
        isCrossfading = false
        notifyCrossfadeActive(false)
        waitingReadySinceMs = 0L
        activeBufferingSinceMs = 0L
        if (shouldRestoreCurrentVolume) {
            currentPlayer.volume = baseVolume
        }
        setPauseAtEndOfMediaItems(currentPlayer, false)
        setPauseAtEndOfMediaItems(nextPlayer, false)
        activeCrossfadeDurationMs = 0L
        outgoingMediaId = null
        outgoingExpectedEndAtMs = 0L
        resetNextPlayer()
        updateUiState()
    }

    private fun resetNextPlayer() {
        nextPlayer.pause()
        nextPlayer.stop()
        nextPlayer.clearMediaItems()
        nextPlayer.volume = 0f
        setPauseAtEndOfMediaItems(nextPlayer, false)
        preloadedIndex = C.INDEX_UNSET
        preloadedMediaId = null
    }

    private fun buildQueueSnapshot(player: ExoPlayer): List<MediaItem> =
        List(player.mediaItemCount) { index -> player.getMediaItemAt(index) }

    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        handler.post(monitorRunnable)
    }

    private fun stopMonitoring() {
        isMonitoring = false
        handler.removeCallbacks(monitorRunnable)
    }

    private fun notifyCrossfadeActive(active: Boolean) {
        if (lastReportedCrossfadeActive == active) return
        lastReportedCrossfadeActive = active
        onCrossfadeActiveChanged(active)
    }

    private fun updateUiState(crossfadeElapsedMs: Long = 0L) {
        if (!enabled) {
            _uiState.value = CrossfadeUiState()
            return
        }

        val previousDisplayItem = _uiState.value.displayMediaItem
        val normalizedCurrentMediaItem = normalizeDisplayMediaItem(currentPlayer.currentMediaItem)
        val normalizedIncomingMediaItem = if (isCrossfading) {
            normalizeDisplayMediaItem(nextPlayer.currentMediaItem)
        } else {
            null
        }

        val displayPlayer = currentPlayer
        val fallbackPlayer = if (displayPlayer === currentPlayer) nextPlayer else currentPlayer

        val normalizedDisplayMediaItem = when {
            isCrossfading -> normalizedCurrentMediaItem
                ?: previousDisplayItem
                ?: normalizedIncomingMediaItem
            else -> normalizedCurrentMediaItem ?: previousDisplayItem
        }

        val duration = (displayPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            ?: fallbackPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            ?: if (normalizedDisplayMediaItem != null) 1L else 0L)
        val position = displayPlayer.currentPosition
            .coerceAtLeast(0L)
            .coerceIn(0L, duration.coerceAtLeast(1L))
        val stableDisplayMediaItem = if (
            previousDisplayItem != null &&
            normalizedDisplayMediaItem != null &&
            previousDisplayItem.mediaId == normalizedDisplayMediaItem.mediaId
        ) {
            previousDisplayItem
        } else {
            normalizedDisplayMediaItem
        }
        _uiState.value = CrossfadeUiState(
            isEnabled = true,
            isActive = isCrossfading,
            isHighlightActive = isCrossfading && crossfadeElapsedMs <= activeCrossfadeDurationMs.coerceAtMost(10_000L),
            progress = if (isCrossfading && activeCrossfadeDurationMs > 0L) {
                (crossfadeElapsedMs.toFloat() / activeCrossfadeDurationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            },
            displayPosition = position,
            displayDuration = duration,
            displayMediaItem = stableDisplayMediaItem,
            incomingMediaItem = normalizedIncomingMediaItem,
        )
        if (previousDisplayItem?.mediaId != stableDisplayMediaItem?.mediaId) {
            onDisplayItemChanged?.invoke(stableDisplayMediaItem)
        }
    }

    private fun normalizeDisplayMediaItem(mediaItem: MediaItem?): MediaItem? {
        if (mediaItem == null) return null

        val metadata = mediaItem.mediaMetadata
        val normalizedMetadata = metadata.buildUpon()
            .setTitle(cleanPrefix(cleanPrefix(metadata.title?.toString().orEmpty())))
            .setArtist(cleanPrefix(cleanPrefix(metadata.artist?.toString().orEmpty())))
            .setAlbumTitle(cleanPrefix(cleanPrefix(metadata.albumTitle?.toString().orEmpty())))
            .build()

        return mediaItem.buildUpon()
            .setMediaMetadata(normalizedMetadata)
            .build()
    }

    private fun setPauseAtEndOfMediaItems(player: ExoPlayer, enabled: Boolean) {
        runCatching {
            pauseAtEndMethod?.invoke(player, enabled)
        }
    }
}
