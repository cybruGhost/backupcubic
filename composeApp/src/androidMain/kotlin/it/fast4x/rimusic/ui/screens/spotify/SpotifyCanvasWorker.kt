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
import kotlinx.coroutines.launch
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

// API Configuration
object SpotifyApiConfig {
    // Configuration URL
    private const val CONFIG_URL = "https://v0-spotify-playlist-csv.vercel.app/carolwillbemywife_config.json"
    
    // Default URLs
    private const val DEFAULT_MATCH_API = "https://v0-spotifyapishit.vercel.app/api/spotify/match"
    private const val DEFAULT_CANVAS_API = "https://v0-spotifyapishit.vercel.app/api/canvas"
    
    // Actual URLs used
    var MATCH_API: String = DEFAULT_MATCH_API
    var CANVAS_API: String = DEFAULT_CANVAS_API
    
    // Thresholds for detection
    const val THUMBNAIL_CHANGE_DEBOUNCE_MS = 500L // Faster response
    const val MIN_MATCH_SCORE = 80.0
    
    // Configuration state
    var isConfigLoaded: Boolean = false
    var usingJsonConfig: Boolean = false
    
    // Track cache for canvas URLs
    private val canvasCache = mutableMapOf<String, String>() // trackId -> canvasUrl
    
    suspend fun loadConfigIfNeeded(context: android.content.Context) {
        if (isConfigLoaded) return
        
        try {
            val config = fetchConfigFromUrl(context)
            if (config != null) {
                try {
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
                    usingJsonConfig = false
                }
            }
        } catch (e: Exception) {
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
    
    fun getCanvasFromCache(trackId: String): String? {
        return canvasCache[trackId]
    }
    
    fun cacheCanvas(trackId: String, canvasUrl: String) {
        canvasCache[trackId] = canvasUrl
    }
    
    fun clearCacheForTrack(trackId: String) {
        canvasCache.remove(trackId)
    }
    
    fun clearAllCache() {
        canvasCache.clear()
    }
}

object SpotifyCanvasState {
    var currentCanvasUrl: String? by mutableStateOf(null)
    var isLoading: Boolean by mutableStateOf(false)
    var error: String? by mutableStateOf(null)
    var logEntries: MutableList<LogEntry> by mutableStateOf(mutableListOf())
    var currentTrackId: String? by mutableStateOf(null)
    var currentMediaItemId: String? by mutableStateOf(null)
    var currentSongTitle: String? by mutableStateOf(null)
    var isPlaying: Boolean by mutableStateOf(false)
    var lastFetchTime: Long by mutableStateOf(0L)
    var configSource: String by mutableStateOf("Loading...")
    var lastProcessedMediaId: String? by mutableStateOf(null)
    
    private const val MAX_LOG_ENTRIES = 20
    
    fun addLog(message: String, type: LogType = LogType.INFO) {
        synchronized(logEntries) {
            logEntries.add(LogEntry(message, type))
            if (logEntries.size > MAX_LOG_ENTRIES) {
                logEntries = logEntries.takeLast(MAX_LOG_ENTRIES).toMutableList()
            }
        }
    }
    
    fun clearForNewSong(mediaId: String) {
        currentCanvasUrl = null
        currentTrackId = null
        error = null
        isLoading = false
        isPlaying = false
        currentMediaItemId = mediaId
        lastProcessedMediaId = mediaId
        addLog("Cleared for new song: $mediaId", LogType.INFO)
    }
    
    fun clearAll() {
        currentCanvasUrl = null
        currentTrackId = null
        currentMediaItemId = null
        currentSongTitle = null
        error = null
        isLoading = false
        isPlaying = false
        lastFetchTime = 0L
        lastProcessedMediaId = null
        addLog("All canvas state cleared", LogType.INFO)
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
    val showLogs by rememberPreference("showSpotifyCanvasLogs", false)
    
    // Load configuration once when enabled
    LaunchedEffect(isCanvasEnabled) {
        if (isCanvasEnabled && !SpotifyApiConfig.isConfigLoaded) {
            withContext(Dispatchers.IO) {
                SpotifyApiConfig.loadConfigIfNeeded(context)
            }
            
            if (SpotifyApiConfig.usingJsonConfig) {
                SpotifyCanvasState.updateConfigSource("JSON Config")
                if (showLogs) {
                    SpotifyCanvasState.addLog("Loaded APIs from JSON config", LogType.SUCCESS)
                }
            } else {
                SpotifyCanvasState.updateConfigSource("Default URLs")
                if (showLogs) {
                    SpotifyCanvasState.addLog("Using default API URLs", LogType.INFO)
                }
            }
        }
    }
    
    LaunchedEffect(isCanvasEnabled) {
        if (!isCanvasEnabled) {
            SpotifyCanvasState.clearAll()
            CanvasPlayerManager.stopAndClear()
            SpotifyApiConfig.clearAllCache()
        }
    }
    
    // Monitor player state for song changes
    LaunchedEffect(binder?.player, isCanvasEnabled) {
        if (binder == null || !isCanvasEnabled) return@LaunchedEffect
        
        snapshotFlow { 
            Triple(
                binder.player.currentMediaItem?.mediaId,
                binder.player.currentMediaItem?.mediaMetadata?.title?.toString() ?: "",
                binder.player.currentMediaItem?.mediaMetadata?.artist?.toString() ?: ""
            )
        }.collect { (mediaId, title, artist) ->
            if (mediaId == null || title.isBlank() || artist.isBlank()) {
                return@collect
            }
            
            // Check if song changed
            val songChanged = mediaId != SpotifyCanvasState.lastProcessedMediaId
            
            if (songChanged) {
                if (showLogs) {
                    SpotifyCanvasState.addLog("Song changed: $title - $artist", LogType.INFO)
                }
                
                // Clear everything for the new song
                SpotifyCanvasState.clearForNewSong(mediaId)
                CanvasPlayerManager.stopAndClear()
                
                // Start fetching canvas for new song
                launch(Dispatchers.IO) {
                    fetchCanvasForSong(context, title, artist, showLogs, mediaId)
                }
            } else {
                // Same song, check if we need to update play state
                if (binder.player.playWhenReady != SpotifyCanvasState.isPlaying) {
                    SpotifyCanvasState.isPlaying = binder.player.playWhenReady
                    CanvasPlayerManager.updatePlayState(binder.player.playWhenReady)
                }
            }
        }
    }
    
    // Monitor playback state
    LaunchedEffect(binder?.player) {
        if (binder == null) return@LaunchedEffect
        
        snapshotFlow { 
            Pair(
                binder.player.playbackState,
                binder.player.playWhenReady
            )
        }.collect { (playbackState, playWhenReady) ->
            when (playbackState) {
                Player.STATE_ENDED -> {
                    CanvasPlayerManager.stopAndClear()
                    SpotifyCanvasState.isPlaying = false
                    if (showLogs) {
                        SpotifyCanvasState.addLog("Song ended - canvas stopped", LogType.INFO)
                    }
                }
            }
        }
    }
        
    DisposableEffect(Unit) {
        onDispose {
            SpotifyCanvasState.clearAll()
            CanvasPlayerManager.stopAndClear()
        }
    }
}

private suspend fun fetchCanvasForSong(
    context: android.content.Context,
    title: String,
    artist: String,
    showLogs: Boolean,
    mediaId: String
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
            // Only update if still the same song
            if (mediaId == SpotifyCanvasState.currentMediaItemId) {
                SpotifyCanvasState.currentCanvasUrl = canvasUrl
                SpotifyCanvasState.isPlaying = true
                SpotifyCanvasState.error = null
                SpotifyCanvasState.lastFetchTime = System.currentTimeMillis()
                
                if (showLogs) {
                    SpotifyCanvasState.addLog("Canvas loaded successfully", LogType.SUCCESS)
                }
            } else {
                if (showLogs) {
                    SpotifyCanvasState.addLog("Canvas fetched but song changed, discarding", LogType.WARNING)
                }
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
        .cache(Cache(cacheDir, 20 * 1024 * 1024)) // 20MB cache
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            // Cache for 10 minutes
            response.newBuilder()
                .header("Cache-Control", "public, max-age=600")
                .build()
        }
        .build()
    
    try {
        // Step 1: Match YouTube video to Spotify track
        val encodedTitle = URLEncoder.encode(title, "UTF-8")
        val encodedArtist = URLEncoder.encode(artist, "UTF-8")
        
        val matchUrl = "${SpotifyApiConfig.MATCH_API}?videoTitle=$encodedTitle&author=$encodedArtist&authorVerified=true"
        
        if (showLogs) {
            SpotifyCanvasState.addLog("Finding Spotify track...", LogType.LOADING)
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
            SpotifyCanvasState.addLog("Found track: ${trackId.take(8)}...", LogType.SUCCESS)
        }
        
        // Check cache first
        val cachedCanvas = SpotifyApiConfig.getCanvasFromCache(trackId)
        if (cachedCanvas != null) {
            if (showLogs) {
                SpotifyCanvasState.addLog("Using cached canvas", LogType.INFO)
            }
            return cachedCanvas
        }
        
        // Step 2: Fetch Canvas URL
        val canvasUrl = "${SpotifyApiConfig.CANVAS_API}?trackId=$trackId"
        
        if (showLogs) {
            SpotifyCanvasState.addLog("Loading canvas video...", LogType.LOADING)
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
        val canvasUrlResult = parseCanvasUrl(canvasJson)
        
        // Cache the result
        if (canvasUrlResult != null) {
            SpotifyApiConfig.cacheCanvas(trackId, canvasUrlResult)
        }
        
        return canvasUrlResult
        
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