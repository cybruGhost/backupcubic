package it.fast4x.rimusic.ui.screens.cubicjam

import android.content.Context
import androidx.media3.common.MediaItem
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.painterResource
import app.kreate.android.R
import me.knighthat.utils.isNetworkAvailable
import timber.log.Timber
import it.fast4x.innertube.Innertube
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AuthResponse(
    val success: Boolean,
    val user: UserInfo,
    val session: SessionInfo
)

@Serializable
data class UserInfo(
    val id: String,
    val email: String,
    val profile_id: String,
    val username: String,
    val display_name: String? = null,
    val avatar_url: String? = null,
    val friend_code: String
)

@Serializable
data class SessionInfo(
    val access_token: String,
    val refresh_token: String,
    val expires_at: Long,
    val expires_in: Int
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class SignupRequest(
    val email: String,
    val password: String,
    val username: String
)

@Serializable
data class ActivityUpdate(
    val track_id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artwork_url: String? = null,
    val duration_ms: Long,
    val position_ms: Long,
    val is_playing: Boolean
)

class CubicJamManager(
    private val context: Context,
    private val getToken: () -> String?,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val BASE_URL = "https://dkbvirgavjojuyaazzun.supabase.co/functions/v1"
    }

    private var lastToken: String? = null
    private var lastMediaItem: MediaItem? = null
    private var lastPosition: Long = 0L
    private var isStopped = false
    private val cubicJamScope = externalScope
    private var refreshJob: Job? = null

    fun onPlayingStateChanged(
        mediaItem: MediaItem?,
        isPlaying: Boolean,
        position: Long = 0L,
        duration: Long = 0L,
        now: Long = System.currentTimeMillis(),
        getCurrentPosition: (() -> Long)? = null,
        isPlayingProvider: (() -> Boolean)? = null
    ) {
        if (isStopped) return
        val token = getToken() ?: return
        if (token.isEmpty()) return

        if (!isNetworkAvailable(context)) {
            Timber.tag("CubicJam").d("No network available")
            return
        }

        refreshJob?.cancel()
        refreshJob = null

        if (token != lastToken) {
            lastToken = token
        }

        lastMediaItem = mediaItem
        lastPosition = position
        if (mediaItem == null) {
            sendStoppedActivity()
            return
        }
        
        if (isPlaying) {
            sendPlayingPresence(mediaItem, position, duration, now)
            val currentIsPlaying = isPlaying
            val currentPosition = position
            startRefreshJob(
                isPlayingProvider = { currentIsPlaying },
                mediaItem = mediaItem,
                getCurrentPosition = { currentPosition },
                pausedPosition = position,
                duration = duration,
                startTime = now
            )
        } else {
            sendPausedPresence(duration, now, position)
            val currentIsPlaying = isPlaying
            val currentPosition = position
            startRefreshJob(
                isPlayingProvider = { currentIsPlaying },
                mediaItem = mediaItem,
                getCurrentPosition = { currentPosition },
                pausedPosition = position,
                duration = duration,
                startTime = now
            )
        }
    }

    private fun sendPausedPresence(duration: Long, now: Long, pausedPosition: Long) {
        if (isStopped) return
        val mediaItem = lastMediaItem ?: return
        cubicJamScope.launch {
            if (isStopped) return@launch
            sendActivityUpdate(
                mediaItem = mediaItem,
                position = pausedPosition,
                duration = duration,
                isPlaying = false
            )
        }
    }

    private fun sendPlayingPresence(mediaItem: MediaItem, position: Long, duration: Long, now: Long) {
        cubicJamScope.launch {
            sendActivityUpdate(
                mediaItem = mediaItem,
                position = position,
                duration = duration,
                isPlaying = true
            )
        }
    }

    private suspend fun sendActivityUpdate(
        mediaItem: MediaItem,
        position: Long,
        duration: Long,
        isPlaying: Boolean
    ) {
        val token = getToken() ?: return
        if (token.isEmpty()) return

        try {
            val activityData = createActivityData(mediaItem, position, duration, isPlaying)
            
            Timber.tag("CubicJam").d("Sending activity: ${activityData.title} by ${activityData.artist}")
            
            val response = Innertube.client.post("$BASE_URL/update-activity") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(activityData)
            }

            if (response.status.value in 200..299) {
                Timber.tag("CubicJam").d("✅ Activity updated successfully")
            } else {
                val error = try {
                    response.body<String>()
                } catch (e: Exception) {
                    "Failed to parse error"
                }
                Timber.tag("CubicJam").e("❌ Activity update failed: ${response.status}, $error")
            }
        } catch (e: Exception) {
            Timber.tag("CubicJam").e(e, "❌ Network error: ${e.message}")
        }
    }

    private suspend fun createActivityData(
        mediaItem: MediaItem,
        position: Long,
        duration: Long,
        isPlaying: Boolean
    ): ActivityUpdate {
        val title = mediaItem.mediaMetadata.title?.toString() ?: "Unknown Title"
        val artist = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown Artist"
        val album = mediaItem.mediaMetadata.albumTitle?.toString()
        val artworkUri = mediaItem.mediaMetadata.artworkUri
        
        val trackId = mediaItem.mediaId ?: "unknown_${System.currentTimeMillis()}"
        
        val artworkUrl = if (artworkUri?.scheme?.startsWith("http") == true) {
            artworkUri.toString()
        } else {
            null
        }
        
        return ActivityUpdate(
            track_id = trackId,
            title = title,
            artist = artist,
            album = album,
            artwork_url = artworkUrl,
            duration_ms = duration,
            position_ms = position,
            is_playing = isPlaying
        )
    }

    private fun sendStoppedActivity() {
        cubicJamScope.launch {
            Timber.tag("CubicJam").d("Media stopped, not sending update")
        }
    }

    private fun startRefreshJob(
        isPlayingProvider: () -> Boolean,
        mediaItem: MediaItem,
        getCurrentPosition: () -> Long,
        pausedPosition: Long,
        duration: Long,
        startTime: Long
    ) {
        refreshJob = cubicJamScope.launch {
            while (isActive && !isStopped) {
                delay(15_000L)
                if (!isNetworkAvailable(context)) {
                    continue
                }
                val isPlaying = isPlayingProvider()
                if (isPlaying) {
                    val pos = getCurrentPosition()
                    sendPlayingPresence(mediaItem, pos, duration, startTime)
                } else {
                    sendPausedPresence(duration, System.currentTimeMillis(), pausedPosition)
                }
            }
        }
    }

    fun onStop() {
        isStopped = true
        refreshJob?.cancel()
        cubicJamScope.cancel()
    }
}

// Authentication functions
suspend fun login(email: String, password: String, context: Context): Result<AuthResponse> {
    return runCatching {
        val response = Innertube.client.post("https://dkbvirgavjojuyaazzun.supabase.co/functions/v1/mobile-auth") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }
        
        val authResponse = response.body<AuthResponse>()
        
        if (authResponse.success) {
            val prefs = context.getSharedPreferences("cubic_jam_prefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("bearer_token", authResponse.session.access_token)
                putString("refresh_token", authResponse.session.refresh_token)
                putString("user_id", authResponse.user.id)
                putString("profile_id", authResponse.user.profile_id)
                putString("username", authResponse.user.username)
                putString("email", authResponse.user.email)
                putString("display_name", authResponse.user.display_name)
                putString("friend_code", authResponse.user.friend_code)
                putBoolean("is_enabled", true)
                apply()
            }
            Timber.tag("CubicJam").d("✅ Login successful for ${authResponse.user.email}")
        }
        
        authResponse
    }
}

suspend fun signup(email: String, password: String, username: String, context: Context): Result<AuthResponse> {
    return runCatching {
        val response = Innertube.client.post("https://dkbvirgavjojuyaazzun.supabase.co/functions/v1/mobile-auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(SignupRequest(email, password, username))
        }
        
        val authResponse = response.body<AuthResponse>()
        
        if (authResponse.success) {
            val prefs = context.getSharedPreferences("cubic_jam_prefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("bearer_token", authResponse.session.access_token)
                putString("refresh_token", authResponse.session.refresh_token)
                putString("user_id", authResponse.user.id)
                putString("profile_id", authResponse.user.profile_id)
                putString("username", authResponse.user.username)
                putString("email", authResponse.user.email)
                putString("display_name", authResponse.user.display_name)
                putString("friend_code", authResponse.user.friend_code)
                putBoolean("is_enabled", true)
                apply()
            }
            Timber.tag("CubicJam").d("✅ Signup successful for ${authResponse.user.email}")
        }
        
        authResponse
    }
}

suspend fun refreshToken(refreshToken: String, context: Context): Result<AuthResponse> {
    return runCatching {
        val response = Innertube.client.post("https://dkbvirgavjojuyaazzun.supabase.co/functions/v1/mobile-auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("refresh_token" to refreshToken))
        }
        
        val authResponse = response.body<AuthResponse>()
        
        if (authResponse.success) {
            val prefs = context.getSharedPreferences("cubic_jam_prefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("bearer_token", authResponse.session.access_token)
                putString("refresh_token", authResponse.session.refresh_token)
                apply()
            }
            Timber.tag("CubicJam").d("✅ Token refreshed successfully")
        }
        
        authResponse
    }
}