package it.fast4x.rimusic.ui.screens.cubicjam

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import it.fast4x.innertube.Innertube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import me.knighthat.utils.ImageProcessor
import me.knighthat.utils.isNetworkAvailable
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

class CubicJamManager(
    private val context: Context,
    private val getToken: () -> String?,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val API_ENDPOINT = "https://dkbvirgavjojuyaazzun.supabase.co/functions/v1/update-activity"
        private const val TEMP_FILE_HOST = "https://litterbox.catbox.moe/resources/internals/api.php"
        private const val MAX_DIMENSION = 1024
        private const val MAX_FILE_SIZE_BYTES = 2L * 1024 * 1024
    }

    private var lastToken: String? = null
    private var lastMediaItem: MediaItem? = null
    private var lastPosition: Long = 0L
    private var isStopped = false
    private val cubicJamScope = externalScope
    private var refreshJob: Job? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

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
            // Store current values to avoid calling lambdas later
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
            // Store current values to avoid calling lambdas later
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

    /**
     * Send the "Paused" activity with frozen time
     */
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

    /**
     * Send "Playing" activity
     */
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

    /**
     * Send activity update to Cubic Jam API
     */
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
            
            Timber.tag("CubicJam").d("Sending activity")
            
            val response = Innertube.client.post(API_ENDPOINT) {
                headers.append("Authorization", "Bearer $token")
                setBody(activityData)
            }

            if (response.status.value in 200..299) {
                Timber.tag("CubicJam").d("✅ Activity updated successfully")
            } else {
                val error = response.body<String>()
                Timber.tag("CubicJam").e("❌ Activity update failed: $error")
            }
        } catch (e: Exception) {
            Timber.tag("CubicJam").e(e, "❌ Network error: ${e.message}")
        }
    }

    /**
     * Create activity data from MediaItem
     */
    private suspend fun createActivityData(
        mediaItem: MediaItem,
        position: Long,
        duration: Long,
        isPlaying: Boolean
    ): Map<String, Any> {
        val title = mediaItem.mediaMetadata.title?.toString() ?: "Unknown Title"
        val artist = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown Artist"
        val album = mediaItem.mediaMetadata.albumTitle?.toString()
        val artworkUri = mediaItem.mediaMetadata.artworkUri
        
        val trackId = mediaItem.mediaId ?: "unknown_${System.currentTimeMillis()}"
        
        // Get artwork URL if available - call from coroutine scope
        val artworkUrl = artworkUri?.let { uri ->
            withContext(Dispatchers.IO) {
                getArtworkUrl(uri)
            }
        }
        
        return mapOf(
            "track_id" to trackId,
            "title" to title,
            "artist" to artist,
            "album" to (album ?: ""),
            "artwork_url" to (artworkUrl ?: ""),
            "duration_ms" to duration,
            "position_ms" to position,
            "is_playing" to isPlaying
        )
    }

    /**
     * Upload artwork to temp host
     */
    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun getArtworkUrl(artworkUri: Uri?): String? = runCatching {
        val uploadableUri = ImageProcessor.compressArtwork(
            context,
            artworkUri,
            MAX_DIMENSION,
            MAX_DIMENSION,
            MAX_FILE_SIZE_BYTES
        ) ?: return@runCatching null
        
        if (uploadableUri.scheme?.startsWith("http") == true) {
            return@runCatching uploadableUri.toString()
        }

        Innertube.client
            .submitFormWithBinaryData(
                url = TEMP_FILE_HOST,
                formData = formData {
                    val (mimeType, fileData) = with(context.contentResolver) {
                        getType(uploadableUri)!! to openInputStream(uploadableUri)!!.readBytes()
                    }

                    append("reqtype", "fileupload")
                    append("time", "1h")
                    append("fileToUpload", fileData, io.ktor.http.Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"${System.currentTimeMillis()}\"")
                        append(HttpHeaders.ContentType, mimeType)
                    })
                }
            )
            .bodyAsText()
            .toUri()
            .toString()
    }.getOrNull()

    private fun sendStoppedActivity() {
        cubicJamScope.launch {
            Timber.tag("CubicJam").d("Media stopped, not sending update")
        }
    }

    /**
     * Start refresh job (15 seconds)
     */
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

    /**
     * Stop all Cubic Jam updates
     */
    fun onStop() {
        isStopped = true
        refreshJob?.cancel()
        cubicJamScope.cancel()
    }
}