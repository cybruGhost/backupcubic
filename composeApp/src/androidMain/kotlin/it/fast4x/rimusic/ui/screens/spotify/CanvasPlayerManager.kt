package it.fast4x.rimusic.ui.screens.spotify

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import timber.log.Timber

object CanvasPlayerManager {
    private var currentPlayer: ExoPlayer? = null
    private var currentPlayerView: PlayerView? = null
    private var currentCanvasUrl: String? = null
    private var _currentMediaItemId: String? = null
    private var isPlayerActive = false
    private var lastSetupTime = 0L
    
    // Memory optimization
    private const val PLAYER_RECYCLE_THRESHOLD = 5000L // 5 seconds
    
    fun getCurrentCanvasUrl(): String? = currentCanvasUrl
    fun getCurrentMediaItemId(): String? = _currentMediaItemId
    
    fun isPlayingForMediaItem(mediaItemId: String?): Boolean {
        return _currentMediaItemId == mediaItemId && currentCanvasUrl != null
    }
    
    fun setupPlayer(
        context: Context,
        canvasUrl: String,
        isPlaying: Boolean,
        mediaItemId: String? = null
    ): PlayerView {
        val now = System.currentTimeMillis()
        
        // Check if we should reuse player (same media and within threshold)
        val shouldReuse = currentCanvasUrl == canvasUrl && 
                         _currentMediaItemId == mediaItemId &&
                         isPlayerActive &&
                         (now - lastSetupTime) < PLAYER_RECYCLE_THRESHOLD
        
        if (shouldReuse && currentPlayerView != null) {
            Timber.d("CanvasPlayer: Reusing player for mediaId: ${mediaItemId?.take(8)}")
            currentPlayer?.playWhenReady = isPlaying
            currentPlayer?.repeatMode = if (isPlaying) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            return currentPlayerView!!
        }
        
        // Release old player if exists
        if (currentCanvasUrl != canvasUrl || _currentMediaItemId != mediaItemId) {
            releasePlayer()
        }
        
        Timber.d("CanvasPlayer: Creating new player for mediaId: ${mediaItemId?.take(8)}")
        
        val player = ExoPlayer.Builder(context)
            .setSeekForwardIncrementMs(15000)
            .setSeekBackIncrementMs(5000)
            .build()
        
        val mediaItem = MediaItem.Builder()
            .setUri(canvasUrl)
            .setMimeType(MimeTypes.VIDEO_MP4)
            .build()
        
        player.setMediaItem(mediaItem)
        player.playWhenReady = isPlaying
        player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        player.repeatMode = if (isPlaying) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        
        // Memory efficient listener - only tracks errors
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Timber.e("CanvasPlayer error: ${error.message}")
                releasePlayer()
            }
        })
        
        val playerView = PlayerView(context).apply {
            this.player = player
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        
        // Prepare but don't auto-start
        player.prepare()
        if (!isPlaying) {
            player.pause()
        }
        
        currentPlayer = player
        currentPlayerView = playerView
        currentCanvasUrl = canvasUrl
        _currentMediaItemId = mediaItemId
        isPlayerActive = true
        lastSetupTime = now
        
        return playerView
    }
    
    fun releasePlayer() {
        currentPlayer?.let { player ->
            Timber.d("CanvasPlayer: Releasing player for mediaId: ${_currentMediaItemId?.take(8)}")
            player.stop()
            player.release()
        }
        currentPlayer = null
        currentPlayerView = null
        currentCanvasUrl = null
        _currentMediaItemId = null
        isPlayerActive = false
    }
    
    fun updatePlayState(isPlaying: Boolean) {
        currentPlayer?.let { player ->
            player.playWhenReady = isPlaying
            if (isPlaying && !player.isPlaying) {
                player.play()
                player.repeatMode = Player.REPEAT_MODE_ONE
            } else if (!isPlaying) {
                player.pause()
                player.repeatMode = Player.REPEAT_MODE_OFF
            }
        }
    }
    
    fun stopLooping() {
        currentPlayer?.let { player ->
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.pause()
        }
    }
    
    fun stopAndClearForSongEnd() {
        currentPlayer?.let { player ->
            player.stop()
            player.repeatMode = Player.REPEAT_MODE_OFF
        }
        currentCanvasUrl = null
        _currentMediaItemId = null
    }
    
    fun isActive(): Boolean = isPlayerActive
    
    fun forceCleanup() {
        releasePlayer()
        Timber.d("CanvasPlayer: Force cleanup complete")
    }
}