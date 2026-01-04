package it.fast4x.rimusic.ui.screens.spotify

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

object CanvasPlayerManager {
    private var currentPlayer: ExoPlayer? = null
    private var currentPlayerView: PlayerView? = null
    private var currentCanvasUrl: String? = null
    private var currentMediaItemId: String? = null
    
    // PUBLIC getter for current URL (fixes the private access error)
    fun getCurrentCanvasUrl(): String? = currentCanvasUrl
    
    fun isPlayingForMediaItem(mediaItemId: String?): Boolean {
        return currentMediaItemId == mediaItemId && currentCanvasUrl != null
    }
    
    // OPTIMIZED: Add lifecycle tracking
    private var isPlayerActive = false
    
    fun setupPlayer(
        context: Context,
        canvasUrl: String,
        isPlaying: Boolean,
        mediaItemId: String? = null
    ): PlayerView {
        currentMediaItemId = mediaItemId
        
        // OPTIMIZATION: Reuse player if same media
        if (currentMediaItemId == mediaItemId && currentCanvasUrl == canvasUrl && 
            currentPlayerView != null && isPlayerActive) {
            currentPlayer?.playWhenReady = isPlaying
            currentPlayer?.repeatMode = if (isPlaying) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            return currentPlayerView!!
        }
        
        // Release old player properly
        releasePlayer()
        
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
        player.prepare()
        player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        
        // 🎯 OPTIMIZED LOOPING: Sync with song playback
        player.repeatMode = if (isPlaying) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        
        // Add listener to stop infinite loops
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                // If player ended and we're not playing audio, don't restart
                if (playbackState == Player.STATE_ENDED && !isPlaying) {
                    player.repeatMode = Player.REPEAT_MODE_OFF
                }
            }
        })
        
        val playerView = PlayerView(context).apply {
            this.player = player
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        
        currentPlayer = player
        currentPlayerView = playerView
        currentCanvasUrl = canvasUrl
        currentMediaItemId = mediaItemId
        isPlayerActive = true
        
        return playerView
    }
    
    fun releasePlayer() {
        currentPlayer?.release()
        currentPlayer = null
        currentPlayerView = null
        currentCanvasUrl = null
        currentMediaItemId = null
        isPlayerActive = false
    }
    
    fun updatePlayState(isPlaying: Boolean) {
        currentPlayer?.let { player ->
            player.playWhenReady = isPlaying
            // OPTIMIZED: Only update repeat mode if it needs to change
            val currentRepeatMode = player.repeatMode
            val desiredRepeatMode = if (isPlaying) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            
            if (currentRepeatMode != desiredRepeatMode) {
                player.repeatMode = desiredRepeatMode
            }
        }
    }
    
    fun stopLooping() {
        currentPlayer?.repeatMode = Player.REPEAT_MODE_OFF
    }
    
    // NEW: Check if player is active
    fun isActive(): Boolean = isPlayerActive
}