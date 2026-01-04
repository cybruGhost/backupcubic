package it.fast4x.rimusic.ui.screens.spotify

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class LogEntry(
    val message: String,
    val type: LogType,
    val timestamp: Long = System.currentTimeMillis()
)

enum class LogType {
    ERROR, SUCCESS, LOADING, INFO, WARNING
}

object SpotifyCanvasState {
    var currentCanvasUrl: String? by mutableStateOf(null)
    var isLoading: Boolean by mutableStateOf(false)
    var error: String? by mutableStateOf(null)
    var logEntries: MutableList<LogEntry> by mutableStateOf(mutableListOf())
    var currentTrackId: String? by mutableStateOf(null)
    var currentMediaItemId: String? by mutableStateOf(null)
    var isPlaying: Boolean by mutableStateOf(false)
    var shouldFetch: Boolean by mutableStateOf(true) // NEW: Control when to fetch
    
    private const val MAX_LOG_ENTRIES = 15
    
    fun addLog(message: String, type: LogType = LogType.INFO) {
        synchronized(logEntries) {
            logEntries.add(LogEntry(message, type))
            // Keep only last N logs
            if (logEntries.size > MAX_LOG_ENTRIES) {
                logEntries = logEntries.takeLast(MAX_LOG_ENTRIES).toMutableList()
            }
        }
    }
    
    fun clear() {
        currentCanvasUrl = null
        currentTrackId = null
        currentMediaItemId = null
        error = null
        isLoading = false
        isPlaying = false
        shouldFetch = true // Reset fetch flag
        addLog("Canvas state cleared", LogType.INFO)
    }
    
    fun clearError() {
        error = null
    }
    
    fun markForFetch() {
        shouldFetch = true
    }
}

@Composable
fun SpotifyCanvasWorker() {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    
    val isCanvasEnabled by rememberPreference("spotifyCanvasEnabled", false)
    val userEmail by rememberPreference("spotifyUserEmail", "")
    val showLogs by rememberPreference("showSpotifyCanvasLogs", false)
    
    LaunchedEffect(binder?.player, isCanvasEnabled, userEmail) {
        if (binder == null || !isCanvasEnabled || userEmail.isEmpty()) {
            SpotifyCanvasState.clear()
            return@LaunchedEffect
        }
        
        snapshotFlow { 
            Triple(
                binder.player.currentMediaItem?.mediaId,
                binder.player.currentMediaItem?.mediaMetadata?.title?.toString() ?: "",
                binder.player.currentMediaItem?.mediaMetadata?.artist?.toString() ?: ""
            )
        }.collect { (mediaId, title, artist) ->
            if (mediaId == null) return@collect
            
            // Check if song changed OR if we need to fetch for current song
            if (mediaId != SpotifyCanvasState.currentMediaItemId || SpotifyCanvasState.shouldFetch) {
                if (showLogs) {
                    if (mediaId != SpotifyCanvasState.currentMediaItemId) {
                        SpotifyCanvasState.addLog("Song changed: $title - $artist", LogType.INFO)
                    } else {
                        SpotifyCanvasState.addLog("Refetching canvas for current song", LogType.INFO)
                    }
                }
                
                // Clear previous state
                SpotifyCanvasState.clear()
                SpotifyCanvasState.currentMediaItemId = mediaId
                SpotifyCanvasState.shouldFetch = false // Reset fetch flag
                
                if (title.isNotBlank() && artist.isNotBlank()) {
                    fetchCanvasForSong(context, title, artist, userEmail, showLogs, mediaId)
                }
            }
        }
    }
    
    // Monitor player state to control video playback AND detect song end
    LaunchedEffect(binder?.player) {
        if (binder == null) return@LaunchedEffect
        
        snapshotFlow { 
            Triple(
                binder.player.currentMediaItem?.mediaId,
                binder.player.playbackState,
                binder.player.playWhenReady
            )
        }.collect { (mediaId, playbackState, playWhenReady) ->
            when {
                // Song ended - stop canvas loop
                playbackState == Player.STATE_ENDED -> {
                    CanvasPlayerManager.stopLooping()
                    if (showLogs) {
                        SpotifyCanvasState.addLog("Song ended - canvas loop stopped", LogType.INFO)
                    }
                }
                
                // Song paused - pause canvas
                !playWhenReady && mediaId == SpotifyCanvasState.currentMediaItemId -> {
                    CanvasPlayerManager.updatePlayState(false)
                    if (showLogs) {
                        SpotifyCanvasState.addLog("Song paused - canvas paused", LogType.INFO)
                    }
                }
                
                // Song resumed - resume canvas
                playWhenReady && mediaId == SpotifyCanvasState.currentMediaItemId -> {
                    CanvasPlayerManager.updatePlayState(true)
                    if (showLogs) {
                        SpotifyCanvasState.addLog("Song resumed - canvas resumed", LogType.INFO)
                    }
                }
            }
        }
    }
    
    // Force refetch when enabled or email changes
    LaunchedEffect(isCanvasEnabled, userEmail) {
        if (isCanvasEnabled && userEmail.isNotEmpty()) {
            SpotifyCanvasState.markForFetch()
        }
    }
        
    DisposableEffect(Unit) {
        onDispose {
            SpotifyCanvasState.clear()
            CanvasPlayerManager.releasePlayer()  // ← ADD THIS LINE!
        }
    }
}

private suspend fun fetchCanvasForSong(
    context: android.content.Context,
    title: String,
    artist: String,
    userEmail: String,
    showLogs: Boolean,
    mediaId: String
) {
    SpotifyCanvasState.isLoading = true
    
    if (showLogs) {
        SpotifyCanvasState.addLog("Fetching canvas for: $title - $artist", LogType.LOADING)
    }
    
    try {
        val canvasUrl = withTimeoutOrNull(10000) { // 10 second timeout
            withContext(Dispatchers.IO) {
                fetchSpotifyCanvas(context, title, artist, userEmail, showLogs)
            }
        }
        
        if (canvasUrl != null) {
            SpotifyCanvasState.currentCanvasUrl = canvasUrl
            SpotifyCanvasState.isPlaying = true // Start playing
            
            if (showLogs) {
                SpotifyCanvasState.addLog("Canvas loaded successfully", LogType.SUCCESS)
                SpotifyCanvasState.addLog("Canvas will loop until song changes", LogType.INFO)
            }
        } else {
            if (showLogs) {
                SpotifyCanvasState.addLog("No canvas available for this track", LogType.WARNING)
            }
        }
    } catch (e: Exception) {
        SpotifyCanvasState.error = "Failed to load canvas"
        if (showLogs) {
            SpotifyCanvasState.addLog("Error: ${e.message}", LogType.ERROR)
        }
    } finally {
        SpotifyCanvasState.isLoading = false
    }
}

private fun fetchSpotifyCanvas(
    context: android.content.Context,
    title: String,
    artist: String,
    userEmail: String,
    showLogs: Boolean
): String? {
    val cacheDir = File(context.cacheDir, "spotify_canvas")
    if (!cacheDir.exists()) cacheDir.mkdirs()
    
    val client = OkHttpClient.Builder()
        .cache(Cache(cacheDir, 5 * 1024 * 1024))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            response.newBuilder()
                .header("Cache-Control", "public, max-age=300")
                .build()
        }
        .build()
    
    val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
    val encodedEmail = java.net.URLEncoder.encode(userEmail, "UTF-8")
    val encodedArtist = java.net.URLEncoder.encode(artist, "UTF-8")
    
    // Step 1: Find Spotify track ID
    val conversionUrl = "https://v0-spotify-playlist-csv.vercel.app/api/youtube-to-spotify" +
        "?title=$encodedTitle" +
        "&email=$encodedEmail" +
        "&author=$encodedArtist"
    
    if (showLogs) {
        SpotifyCanvasState.addLog("Step 1: Finding Spotify track...", LogType.LOADING)
    }
    
    val conversionRequest = Request.Builder()
        .url(conversionUrl)
        .header("Accept", "application/json")
        .header("User-Agent", "CubicMusicApp/1.0")
        .build()
    
    val conversionResponse = try {
        client.newCall(conversionRequest).execute()
    } catch (e: IOException) {
        if (showLogs) {
            SpotifyCanvasState.addLog("Network error: ${e.message}", LogType.ERROR)
        }
        return null
    }
    
    if (!conversionResponse.isSuccessful) {
        if (showLogs) {
            SpotifyCanvasState.addLog("Conversion API error: ${conversionResponse.code}", LogType.WARNING)
        }
        return null
    }
    
    val conversionJson = conversionResponse.body?.use { it.string() } ?: ""
    val trackId = parseTrackId(conversionJson)
    
    if (trackId == null) {
        if (showLogs) {
            SpotifyCanvasState.addLog("Could not extract track ID", LogType.ERROR)
        }
        return null
    }
    
    SpotifyCanvasState.currentTrackId = trackId
    
    if (showLogs) {
        SpotifyCanvasState.addLog("Found track ID: ${trackId.take(8)}...", LogType.SUCCESS)
    }
    
    // Step 2: Fetch Canvas URL
    val canvasUrl = "https://spotifyapi-gamma.vercel.app/api/canvas?trackId=$trackId"
    
    if (showLogs) {
        SpotifyCanvasState.addLog("Step 2: Loading canvas video...", LogType.LOADING)
    }
    
    val canvasRequest = Request.Builder()
        .url(canvasUrl)
        .header("Accept", "application/json")
        .header("User-Agent", "CubicMusicApp/1.0")
        .build()
    
    val canvasResponse = try {
        client.newCall(canvasRequest).execute()
    } catch (e: IOException) {
        if (showLogs) {
            SpotifyCanvasState.addLog("Canvas API network error", LogType.ERROR)
        }
        return null
    }
    
    if (!canvasResponse.isSuccessful) {
        if (showLogs) {
            SpotifyCanvasState.addLog("Canvas API error: ${canvasResponse.code}", LogType.WARNING)
        }
        return null
    }
    
    val canvasJson = canvasResponse.body?.use { it.string() } ?: ""
    return parseCanvasUrl(canvasJson)
}

private fun parseTrackId(json: String): String? {
    return try {
        JSONObject(json).optString("trackId").takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }
}

private fun parseCanvasUrl(json: String): String? {
    return try {
        val canvases = JSONObject(json).optJSONArray("canvasesList")
        canvases?.let {
            if (it.length() > 0) {
                it.getJSONObject(0).optString("canvasUrl").takeIf { url -> 
                    url.isNotBlank() && url.startsWith("https://")
                }
            } else null
        }
    } catch (e: Exception) {
        null
    }
}