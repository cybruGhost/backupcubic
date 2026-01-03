package it.fast4x.rimusic.ui.screens.spotify

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object SpotifyCanvasState {
    var currentCanvasUrl: String? = null
    var isLoading: Boolean = false
    var error: String? = null
    var logMessages: MutableList<String> = mutableListOf()
    var currentTrackId: String? = null  // Changed from private to public
    
    fun clear() {
        currentCanvasUrl = null
        currentTrackId = null
        error = null
        isLoading = false
        logMessages.clear()
    }
}

@Composable
fun SpotifyCanvasWorker() {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    
    val isCanvasEnabled by rememberPreference("spotifyCanvasEnabled", false)
    val userEmail by rememberPreference("spotifyUserEmail", "")
    val showLogs by rememberPreference("showSpotifyCanvasLogs", false)
    
    LaunchedEffect(binder?.player?.currentMediaItem, isCanvasEnabled, userEmail) {
        if (binder == null || !isCanvasEnabled || userEmail.isEmpty()) {
            SpotifyCanvasState.clear()
            return@LaunchedEffect
        }
        
        val mediaItem = binder.player.currentMediaItem ?: return@LaunchedEffect
        val title = mediaItem.mediaMetadata.title?.toString() ?: ""
        val artist = mediaItem.mediaMetadata.artist?.toString() ?: ""
        
        SpotifyCanvasState.clear()
        SpotifyCanvasState.isLoading = true
        
        if (showLogs) {
            SpotifyCanvasState.logMessages.add("Starting canvas fetch for: $title")
        }
        
        try {
            val result = withContext(Dispatchers.IO) {
                fetchSpotifyCanvas(context, title, artist, userEmail, showLogs)
            }
            
            if (showLogs) {
                SpotifyCanvasState.logMessages.add("Canvas fetch completed")
            }
        } catch (e: Exception) {
            SpotifyCanvasState.error = "Failed to load canvas"
            if (showLogs) {
                SpotifyCanvasState.logMessages.add("Error: ${e.message}")
                SpotifyCanvasState.logMessages.add("Tip: Check email or try another song")
            }
        } finally {
            SpotifyCanvasState.isLoading = false
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            SpotifyCanvasState.clear()
        }
    }
}

private suspend fun fetchSpotifyCanvas(
    context: android.content.Context,
    title: String,
    artist: String,
    userEmail: String,
    showLogs: Boolean
): String? {
    val cacheDir = File(context.cacheDir, "spotify_canvas")
    if (!cacheDir.exists()) cacheDir.mkdirs()
    
    val client = OkHttpClient.Builder()
        .cache(Cache(cacheDir, 10 * 1024 * 1024))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            response.newBuilder()
                .header("Cache-Control", "public, max-age=60")
                .build()
        }
        .build()
    
    val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
    val encodedEmail = java.net.URLEncoder.encode(userEmail, "UTF-8")
    val encodedArtist = java.net.URLEncoder.encode(artist, "UTF-8")
    
    val conversionUrl = "https://v0-spotify-playlist-csv.vercel.app/api/youtube-to-spotify" +
        "?title=$encodedTitle" +
        "&email=$encodedEmail" +
        "&author=$encodedArtist"
    
    if (showLogs) {
        SpotifyCanvasState.logMessages.add("Step 1: Finding Spotify track...")
    }
    
    val conversionRequest = Request.Builder()
        .url(conversionUrl)
        .header("Accept", "application/json")
        .build()
    
    val conversionResponse = client.newCall(conversionRequest).execute()
    
    if (!conversionResponse.isSuccessful) {
        val errorCode = conversionResponse.code
        val errorMessage = when (errorCode) {
            401 -> "Token expired. Re-authenticate on website"
            404 -> "Track not found on Spotify"
            429 -> "Too many requests. Try later"
            else -> "API error ($errorCode)"
        }
        SpotifyCanvasState.error = errorMessage
        throw IOException(errorMessage)
    }
    
    val conversionJson = conversionResponse.body?.string() ?: ""
    val trackId = parseTrackId(conversionJson)
    
    if (trackId == null) {
        SpotifyCanvasState.error = "Could not find track"
        return null
    }
    
    SpotifyCanvasState.currentTrackId = trackId
    
    if (showLogs) {
        SpotifyCanvasState.logMessages.add("Found track ID: ${trackId.take(8)}...")
    }
    
    val canvasUrl = "https://spotifyapi-gamma.vercel.app/api/canvas?trackId=$trackId"
    
    if (showLogs) {
        SpotifyCanvasState.logMessages.add("Step 2: Loading canvas video...")
    }
    
    val canvasRequest = Request.Builder()
        .url(canvasUrl)
        .header("Accept", "application/json")
        .build()
    
    val canvasResponse = client.newCall(canvasRequest).execute()
    
    if (!canvasResponse.isSuccessful) {
        if (showLogs) {
            SpotifyCanvasState.logMessages.add("No canvas available for this track")
        }
        return null
    }
    
    val canvasJson = canvasResponse.body?.string() ?: ""
    val canvasVideoUrl = parseCanvasUrl(canvasJson)
    
    SpotifyCanvasState.currentCanvasUrl = canvasVideoUrl
    
    if (showLogs) {
        if (canvasVideoUrl != null) {
            SpotifyCanvasState.logMessages.add("Canvas loaded successfully")
        } else {
            SpotifyCanvasState.logMessages.add("No canvas video found")
        }
    }
    
    return canvasVideoUrl
}

private fun parseTrackId(json: String): String? {
    return try {
        JSONObject(json).getString("trackId")
    } catch (e: Exception) {
        null
    }
}

private fun parseCanvasUrl(json: String): String? {
    return try {
        val canvases = JSONObject(json).getJSONArray("canvasesList")
        if (canvases.length() > 0) {
            canvases.getJSONObject(0).getString("canvasUrl")
        } else null
    } catch (e: Exception) {
        null
    }
}