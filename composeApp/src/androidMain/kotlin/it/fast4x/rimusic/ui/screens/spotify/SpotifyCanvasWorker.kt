package it.fast4x.rimusic.ui.screens.spotify

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class LogEntry(
    val message: String,
    val type: LogType,
    val timestamp: Long = System.currentTimeMillis()
)

enum class LogType {
    ERROR, SUCCESS, LOADING, INFO, WARNING
}

// API Configuration - loads from JSON first, falls back to defaults if needed
object SpotifyApiConfig {
    // Configuration URL
    private const val CONFIG_URL = "https://v0-spotify-playlist-csv.vercel.app/carolwillbemywife_config.json"
    
    // Default URLs (only used if JSON fails to load)
    private const val DEFAULT_MATCH_API = "https://v0-spotifyapishit.vercel.app/api/spotify/match"
    private const val DEFAULT_CANVAS_API = "https://v0-spotifyapishit.vercel.app/api/canvas"
    
    // Actual URLs used (loaded from JSON or defaults)
    var MATCH_API: String = DEFAULT_MATCH_API
    var CANVAS_API: String = DEFAULT_CANVAS_API
    
    // Thresholds for detection
    const val THUMBNAIL_CHANGE_DEBOUNCE_MS = 1000L
    const val MIN_MATCH_SCORE = 100.0
    
    // Configuration state
    var isConfigLoaded: Boolean = false
    var usingJsonConfig: Boolean = false
    
    // Load configuration once on app start
    suspend fun loadConfigIfNeeded(context: android.content.Context) {
        if (isConfigLoaded) return
        
        try {
            // Try to load from JSON config
            val config = fetchConfigFromUrl(context)
            if (config != null) {
                try {
                    // Try to parse the JSON structure
                    val endpoints = config.optJSONObject("api_endpoints")
                    if (endpoints != null) {
                        val matchApi = endpoints.optString("match_api", DEFAULT_MATCH_API)
                        val canvasApi = endpoints.optString("canvas_api", DEFAULT_CANVAS_API)
                        
                        if (matchApi.isNotBlank() && canvasApi.isNotBlank()) {
                            MATCH_API = matchApi
                            CANVAS_API = canvasApi
                            usingJsonConfig = true
                        }
                    }
                } catch (e: Exception) {
                    // JSON parsing failed, use defaults
                    usingJsonConfig = false
                }
            }
        } catch (e: Exception) {
            // Network or other error, use defaults
            usingJsonConfig = false
        } finally {
            isConfigLoaded = true
        }
    }
    
    private fun fetchConfigFromUrl(context: android.content.Context): JSONObject? {
        val cacheDir = File(context.cacheDir, "spotify_config")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        val client = OkHttpClient.Builder()
            .cache(Cache(cacheDir, 1 * 1024 * 1024))
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        
        return try {
            val request = Request.Builder()
                .url(CONFIG_URL)
                .header("Accept", "application/json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return null
            }
            
            val json = response.body?.use { it.string() } ?: return null
            JSONObject(json)
        } catch (e: Exception) {
            null
        }
    }
}

object SpotifyCanvasState {
    var currentCanvasUrl: String? by mutableStateOf(null)
    var isLoading: Boolean by mutableStateOf(false)
    var error: String? by mutableStateOf(null)
    var logEntries: MutableList<LogEntry> by mutableStateOf(mutableListOf())
    var currentTrackId: String? by mutableStateOf(null)
    var currentMediaItemId: String? by mutableStateOf(null)
    var currentThumbnail: String? by mutableStateOf(null)
    var isPlaying: Boolean by mutableStateOf(false)
    var shouldFetch: Boolean by mutableStateOf(true)
    var lastFetchTime: Long by mutableStateOf(0L)
    var configSource: String by mutableStateOf("Loading...")
    
    private const val MAX_LOG_ENTRIES = 15
    
    fun addLog(message: String, type: LogType = LogType.INFO) {
        synchronized(logEntries) {
            logEntries.add(LogEntry(message, type))
            if (logEntries.size > MAX_LOG_ENTRIES) {
                logEntries = logEntries.takeLast(MAX_LOG_ENTRIES).toMutableList()
            }
        }
    }
    
    fun clearCanvas() {
        currentCanvasUrl = null
        currentTrackId = null
        error = null
        isLoading = false
        isPlaying = false
        addLog("Canvas cleared - ready for new song", LogType.INFO)
    }
    
    fun clearFull() {
        clearCanvas()
        currentMediaItemId = null
        currentThumbnail = null
        shouldFetch = true
        lastFetchTime = 0L
        addLog("Full canvas state cleared", LogType.INFO)
    }
    
    fun clearError() {
        error = null
    }
    
    fun markForFetch() {
        shouldFetch = true
        addLog("Marked for fetch on next thumbnail change", LogType.INFO)
    }
    
    fun updateThumbnail(thumbnail: String?) {
        if (thumbnail != currentThumbnail) {
            currentThumbnail = thumbnail
            shouldFetch = true
            if (thumbnail != null) {
                addLog("Thumbnail changed - will fetch new canvas", LogType.INFO)
            }
        }
    }
    
    fun updateConfigSource(source: String) {
        configSource = source
        addLog("Using API config: $source", LogType.INFO)
    }
}

@Composable
fun SpotifyCanvasWorker() {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    
    val isCanvasEnabled by rememberPreference("spotifyCanvasEnabled", false)
    
    // Load configuration once when enabled
    LaunchedEffect(isCanvasEnabled) {
        if (isCanvasEnabled && !SpotifyApiConfig.isConfigLoaded) {
            withContext(Dispatchers.IO) {
                SpotifyApiConfig.loadConfigIfNeeded(context)
            }
            
            if (SpotifyApiConfig.usingJsonConfig) {
                SpotifyCanvasState.updateConfigSource("JSON Config")
                SpotifyCanvasState.addLog("Loaded APIs from JSON config", LogType.SUCCESS)
            } else {
                SpotifyCanvasState.updateConfigSource("Default URLs")
                SpotifyCanvasState.addLog("Using default API URLs", LogType.INFO)
            }
        }
    }
    
    LaunchedEffect(isCanvasEnabled) {
        if (!isCanvasEnabled) {
            SpotifyCanvasState.clearFull()
            CanvasPlayerManager.stopAndClear()
        }
    }
    
    // Thumbnail detection state
    val currentThumbnail by remember {
        derivedStateOf {
            binder?.player?.currentMediaItem?.mediaMetadata?.artworkUri?.toString()
        }
    }
    
    // Monitor player state for thumbnail changes
    LaunchedEffect(binder?.player, isCanvasEnabled) {
        if (binder == null || !isCanvasEnabled) return@LaunchedEffect
        
        snapshotFlow { 
            Triple(
                binder.player.currentMediaItem?.mediaId,
                binder.player.currentMediaItem?.mediaMetadata?.artworkUri?.toString(),
                binder.player.currentMediaItem?.mediaMetadata?.title?.toString() ?: ""
            )
        }.collect { (mediaId, thumbnail, title) ->
            if (mediaId == null) return@collect
            
            // Update thumbnail in state
            SpotifyCanvasState.updateThumbnail(thumbnail)
            
            // Check if we should fetch new canvas
            val shouldFetch = isCanvasEnabled && 
                thumbnail != null && 
                SpotifyCanvasState.shouldFetch &&
                title.isNotBlank() &&
                System.currentTimeMillis() - SpotifyCanvasState.lastFetchTime > SpotifyApiConfig.THUMBNAIL_CHANGE_DEBOUNCE_MS
            
            if (shouldFetch) {
                SpotifyCanvasState.addLog("Detected thumbnail for: $title", LogType.LOADING)
                SpotifyCanvasState.shouldFetch = false
                SpotifyCanvasState.lastFetchTime = System.currentTimeMillis()
                
                // Clear previous canvas immediately
                SpotifyCanvasState.clearCanvas()
                CanvasPlayerManager.stopAndClear()
                
                // Fetch new canvas
                val artist = binder.player.currentMediaItem?.mediaMetadata?.artist?.toString() ?: ""
                if (artist.isNotBlank()) {
                    fetchCanvasForSong(context, title, artist, isCanvasEnabled)
                }
            }
            
            // Update current media item ID
            if (mediaId != SpotifyCanvasState.currentMediaItemId) {
                SpotifyCanvasState.currentMediaItemId = mediaId
            }
        }
    }
    
    // Monitor player playback state
    LaunchedEffect(binder?.player) {
        if (binder == null) return@LaunchedEffect
        
        snapshotFlow { 
            Pair(
                binder.player.playbackState,
                binder.player.playWhenReady
            )
        }.collect { (playbackState, playWhenReady) ->
            when {
                // Song ended - stop canvas
                playbackState == Player.STATE_ENDED -> {
                    CanvasPlayerManager.stopAndClear()
                    SpotifyCanvasState.clearCanvas()
                    SpotifyCanvasState.addLog("Song ended - canvas stopped", LogType.INFO)
                }
                
                // Song paused - pause canvas
                !playWhenReady -> {
                    CanvasPlayerManager.updatePlayState(false)
                }
                
                // Song resumed - resume canvas
                playWhenReady && SpotifyCanvasState.currentCanvasUrl != null -> {
                    CanvasPlayerManager.updatePlayState(true)
                }
            }
        }
    }
        
    DisposableEffect(Unit) {
        onDispose {
            SpotifyCanvasState.clearFull()
            CanvasPlayerManager.stopAndClear()
        }
    }
}

private suspend fun fetchCanvasForSong(
    context: android.content.Context,
    title: String,
    artist: String,
    showLogs: Boolean
) {
    SpotifyCanvasState.isLoading = true
    
    if (showLogs) {
        SpotifyCanvasState.addLog("Fetching canvas for: $title - $artist", LogType.LOADING)
    }
    
    try {
        val canvasUrl = withTimeoutOrNull(10000) {
            withContext(Dispatchers.IO) {
                fetchSpotifyCanvas(context, title, artist, showLogs)
            }
        }
        
        if (canvasUrl != null) {
            SpotifyCanvasState.currentCanvasUrl = canvasUrl
            SpotifyCanvasState.isPlaying = true
            SpotifyCanvasState.error = null
            
            if (showLogs) {
                SpotifyCanvasState.addLog("Canvas loaded successfully", LogType.SUCCESS)
            }
        } else {
            SpotifyCanvasState.error = "No canvas available"
            if (showLogs) {
                SpotifyCanvasState.addLog("No canvas found for this track", LogType.WARNING)
            }
        }
    } catch (e: Exception) {
        SpotifyCanvasState.error = "Failed to load canvas: ${e.message}"
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
    showLogs: Boolean
): String? {
    val cacheDir = File(context.cacheDir, "spotify_canvas")
    if (!cacheDir.exists()) cacheDir.mkdirs()
    
    val client = OkHttpClient.Builder()
        .cache(Cache(cacheDir, 10 * 1024 * 1024))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    try {
        // Step 1: Match YouTube video to Spotify track
        val encodedTitle = URLEncoder.encode(title, "UTF-8")
        val encodedArtist = URLEncoder.encode(artist, "UTF-8")
        
        val matchUrl = "${SpotifyApiConfig.MATCH_API}?videoTitle=$encodedTitle&author=$encodedArtist"
        
        if (showLogs) {
            SpotifyCanvasState.addLog("Step 1: Finding Spotify track...", LogType.LOADING)
            SpotifyCanvasState.addLog("Using API: ${SpotifyApiConfig.MATCH_API}", LogType.INFO)
        }
        
        val matchRequest = Request.Builder()
            .url(matchUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "MusicApp/1.0")
            .build()
        
        val matchResponse = client.newCall(matchRequest).execute()
        
        if (!matchResponse.isSuccessful) {
            if (showLogs) {
                SpotifyCanvasState.addLog("Match API error: ${matchResponse.code}", LogType.WARNING)
            }
            return null
        }
        
        val matchJson = matchResponse.body?.use { it.string() } ?: ""
        val (trackId, matchScore) = parseMatchResponse(matchJson)
        
        if (trackId == null) {
            if (showLogs) {
                SpotifyCanvasState.addLog("No matching track found", LogType.ERROR)
            }
            return null
        }
        
        if (matchScore != null && matchScore < SpotifyApiConfig.MIN_MATCH_SCORE) {
            if (showLogs) {
                SpotifyCanvasState.addLog("Low match confidence: $matchScore", LogType.WARNING)
            }
        }
        
        SpotifyCanvasState.currentTrackId = trackId
        
        if (showLogs) {
            SpotifyCanvasState.addLog("Found track: ${trackId.take(8)}... (score: ${matchScore ?: "N/A"})", LogType.SUCCESS)
        }
        
        // Step 2: Fetch Canvas URL
        val canvasUrl = "${SpotifyApiConfig.CANVAS_API}?trackId=$trackId"
        
        if (showLogs) {
            SpotifyCanvasState.addLog("Step 2: Loading canvas video...", LogType.LOADING)
            SpotifyCanvasState.addLog("Using API: ${SpotifyApiConfig.CANVAS_API}", LogType.INFO)
        }
        
        val canvasRequest = Request.Builder()
            .url(canvasUrl)
            .header("Accept", "application/json")
            .build()
        
        val canvasResponse = client.newCall(canvasRequest).execute()
        
        if (!canvasResponse.isSuccessful) {
            if (showLogs) {
                SpotifyCanvasState.addLog("Canvas API error: ${canvasResponse.code}", LogType.WARNING)
            }
            return null
        }
        
        val canvasJson = canvasResponse.body?.use { it.string() } ?: ""
        return parseCanvasUrl(canvasJson)
        
    } catch (e: IOException) {
        if (showLogs) {
            SpotifyCanvasState.addLog("Network error: ${e.message}", LogType.ERROR)
        }
        return null
    } catch (e: Exception) {
        if (showLogs) {
            SpotifyCanvasState.addLog("Unexpected error: ${e.message}", LogType.ERROR)
        }
        return null
    }
}

private fun parseMatchResponse(json: String): Pair<String?, Double?> {
    return try {
        val obj = JSONObject(json)
        val trackId = obj.optString("trackId").takeIf { it.isNotBlank() }
        val matchScore = obj.optDouble("matchScore").takeIf { it > 0 }
        Pair(trackId, matchScore)
    } catch (e: Exception) {
        Pair(null, null)
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