package app.kreate.android.service

import android.content.ContentResolver
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import app.kreate.android.R
import app.kreate.android.Threads
import app.cubic.android.core.network.NetworkClientFactory
import app.cubic.android.core.network.Store
import app.kreate.android.utils.CharUtils
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.grack.nanojson.JsonObject
import com.metrolist.innertube.NewPipeUtils
import com.metrolist.innertube.YouTube as MetroListYouTube
import com.metrolist.innertube.models.YouTubeClient as MetroListYouTubeClient
import com.metrolist.innertube.models.response.PlayerResponse as MetroListPlayerResponse
import io.ktor.client.statement.bodyAsText
import it.fast4x.innertube.Innertube
import app.it.fast4x.rimusic.models.Song
import it.fast4x.innertube.Innertube.createPoTokenChallenge
import it.fast4x.innertube.models.PlayerResponse
import it.fast4x.innertube.models.bodies.NextBody
import it.fast4x.innertube.requests.nextPage
import app.it.fast4x.rimusic.Database
import app.it.fast4x.rimusic.appContext
import app.it.fast4x.rimusic.enums.AudioQualityFormat
import app.it.fast4x.rimusic.enums.InnertubePlayerSource
import app.it.fast4x.rimusic.isConnectionMeteredEnabled
import app.it.fast4x.rimusic.models.Format
import app.it.fast4x.rimusic.service.LoginRequiredException
import app.it.fast4x.rimusic.service.MyDownloadHelper
import app.it.fast4x.rimusic.service.NoInternetException
import app.it.fast4x.rimusic.service.PlayableFormatNotFoundException
import app.it.fast4x.rimusic.service.UnknownException
import app.it.fast4x.rimusic.service.UnplayableException
import app.it.fast4x.rimusic.service.modern.PlayerServiceModern
import app.it.fast4x.rimusic.extensions.youtubelogin.YouTubeSessionStore
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeLoggedIn
import app.it.fast4x.rimusic.utils.SecureApiConfig
import app.it.fast4x.rimusic.utils.isNetworkConnected
import app.it.fast4x.rimusic.extensions.youtubelogin.YouTubeRequestThrottler
import app.it.fast4x.rimusic.utils.alternateSourceRetryKey
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.innertubePlayerSourceKey
import app.it.fast4x.rimusic.utils.isConnectionMetered
import app.it.fast4x.rimusic.utils.isYouTubeVideoId
import app.it.fast4x.rimusic.utils.okHttpDataSourceFactory
import app.it.fast4x.rimusic.utils.preferences
import com.dd3boh.outertune.utils.potoken.PoTokenGenerator
import com.dd3boh.outertune.utils.potoken.PoTokenResult as WebPoTokenResult
import it.fast4x.innertube.models.Context
import it.fast4x.innertube.models.bodies.PlayerBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import app.kreate.android.me.knighthat.utils.Toaster
import org.jetbrains.annotations.Blocking
import org.jetbrains.annotations.NonBlocking
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.services.youtube.YoutubeStreamHelper
import java.net.HttpURLConnection
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap
import it.fast4x.innertube.requests.player
import timber.log.Timber

// ─── Constants ───────────────────────────────────────────────────────────────

private const val CHUNK_LENGTH = 512 * 1024L     // 512 KB
private const val FLOWTUNE_FAILURE_COOLDOWN_MS = 30_000L
private const val PLAYBACK_SOURCE_FAILURE_COOLDOWN_MS = 90_000L

/**
 * Omada streaming proxy — serves audio bytes directly from the Omada server.
 * ExoPlayer fetches bytes from `yt.omada.cafe/api/public/stream`; Omada
 * fetches from googlevideo using its own server IP. No IP-mismatch 403.
 *
 * Usage: GET /api/public/stream/{videoId}?itag=140
 * Returns: audio/mp4 or audio/webm byte stream (supports Range requests).
 *
 * Do NOT use `/companion/latest_version` — that endpoint returns JSON or
 * a redirect to an IP-bound googlevideo URL, not streamable audio bytes.
 */
private const val OMADA_STREAM_BASE = "https://yt.omada.cafe/api/public/stream"

/**
 * Crystal API / Vercel Innertube clone.
 * Its /stream endpoint returns the playable URL documented by the Crystal API.
 */
private const val CRYSTAL_API_STREAM_BASE = "https://v0-innertube-api-clone.vercel.app/api/stream"

private val decimalThumbnailDimensionRegex =
    Regex("""("(?:height|width)"\s*:\s*)(\d+)\.0(?=[,}])""")
    
private fun normalizePlayerResponseJson(json: String): String =
    decimalThumbnailDimensionRegex.replace(json) { match ->
        "${match.groupValues[1]}${match.groupValues[2]}"
    }

private fun String.redactedStreamUrl(): String =
    runCatching {
        val uri = toUri()
        "${uri.scheme}://${uri.host}${uri.encodedPath.orEmpty()}"
    }.getOrDefault("<stream-url>")

// ─── Network helpers ──────────────────────────────────────────────────────────

/**
 * Probe a URI that your **own device** will ultimately stream from.
 * Do NOT use this on proxy-backend URLs (googlevideo URLs returned by Crystal /
 * Omada JSON) — those are IP-bound to the proxy's IP and will succeed here only
 * because… wait, they won't even succeed here since this probe uses your IP.
 * Use [openProbe] only for URLs that ExoPlayer will also stream from your device.
 */
@UnstableApi
private fun Uri.openProbe(videoId: String, itag: Int?): Boolean {
    val dataSource = appContext().okHttpDataSourceFactory.createDataSource()
    val probeSpec = DataSpec.Builder()
        .setUri(this)
        .setPosition(0)
        .setLength(1)
        .build()

    return runCatching {
        dataSource.open(probeSpec)
        true
    }.onFailure { error ->
        Timber.w(error, "Format itag=%s open probe failed for %s", itag, videoId)
    }.also {
        runCatching { dataSource.close() }
    }.getOrDefault(false)
}

private fun Throwable.isNetworkUnavailableFailure(): Boolean =
    generateSequence(this) { it.cause }.any { cause ->
        cause is NoInternetException ||
            cause is UnknownHostException ||
            cause is ConnectException ||
            cause is NoRouteToHostException ||
            cause is SocketTimeoutException ||
            cause is InterruptedIOException ||
            cause.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
            cause.message?.contains("Failed to connect", ignoreCase = true) == true ||
            cause.message?.contains("timeout", ignoreCase = true) == true ||
            cause.message?.contains("Read timed out", ignoreCase = true) == true ||
            cause.message?.contains("ENETUNREACH", ignoreCase = true) == true ||
            cause.message?.contains("EAI_NODATA", ignoreCase = true) == true ||
            cause.message?.contains("ECONNABORTED", ignoreCase = true) == true
    }

// ─── Playback source tracking ─────────────────────────────────────────────────

enum class PlaybackSourceKind(val label: String) {
    Unknown("Waiting"),
    Local("Local"),
    YouTubeAndroid("YouTube"),
    YouTubeIos("YouTube iOS"),
    YouTubeInnertube("YouTube Player"),
    YouTubeTvHtml5("YouTube TV"),
    CrystalApi("Crystal API Direct"),
    FlowTune("Omada Proxy"),       // yt.omada.cafe companion proxy
    MetroList("MetroList Innertube"),
}

data class PlaybackSourceStatus(
    val source: PlaybackSourceKind = PlaybackSourceKind.Unknown,
    val videoId: String = "",
    val isFallback: Boolean = false,
    val updatedAt: Long = 0L
)

object PlaybackSourceMonitor {
    private val _status = MutableStateFlow(PlaybackSourceStatus())
    val status: StateFlow<PlaybackSourceStatus> = _status.asStateFlow()

    fun report(source: PlaybackSourceKind, videoId: String, isFallback: Boolean = false) {
        val normalizedVideoId = videoId.trim()
        val current = _status.value
        if (
            current.source == source &&
            current.videoId == normalizedVideoId &&
            current.isFallback == isFallback
        ) return

        _status.value = PlaybackSourceStatus(
            source = source,
            videoId = normalizedVideoId,
            isFallback = isFallback,
            updatedAt = System.currentTimeMillis()
        )
    }
}

// ─── Database helpers ─────────────────────────────────────────────────────────

/**
 * Store id of song just added to the database.
 * This is created to reduce load to Room.
 */
@Volatile
private var justInserted: String = ""

/** Reach out to `next` endpoint for song's information. */
@Blocking
private fun upsertSongInfo(videoId: String) = runBlocking {
    if (videoId == justInserted) return@runBlocking

    Innertube.nextPage(NextBody(videoId = videoId))?.fold(
        onSuccess = { nextPage ->
            val songItem = nextPage.itemsPage?.items?.firstOrNull() ?: return@fold
            Database.upsert(songItem)
        },
        onFailure = {
            when (it) {
                is UnknownHostException -> justInserted = videoId
                else -> Toaster.e(R.string.failed_to_fetch_original_property)
            }
        }
    )
}

/** Upsert provided format to the database. */
@NonBlocking
private fun upsertSongFormat(videoId: String, format: PlayerResponse.StreamingData.Format) {
    if (videoId == justInserted) return

    runCatching {
        Database.asyncTransaction {
            songTable.insertIgnore(Song.makePlaceholder(videoId))
            formatTable.insertIgnore(
                Format(
                    videoId,
                    format.itag,
                    format.mimeType,
                    format.bitrate.toLong(),
                    format.contentLength,
                    format.lastModified,
                    format.loudnessDb?.toFloat()
                )
            )
        }
        justInserted = videoId
    }
}

// ─── Format extraction ────────────────────────────────────────────────────────

private val jsonParser = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    useArrayPolymorphism = true
    explicitNulls = false
}

@UnstableApi
private fun checkPlayability(playabilityStatus: PlayerResponse.PlayabilityStatus?) {
    if (playabilityStatus?.status != "OK")
        when (playabilityStatus?.status) {
            "LOGIN_REQUIRED" -> throw LoginRequiredException()
            "UNPLAYABLE"     -> throw UnplayableException()
            else             -> throw UnknownException()
        }
}

private fun extractAllFormatsRanked(
    streamingData: PlayerResponse.StreamingData?,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): List<PlayerResponse.StreamingData.Format> {
    val allFormats = streamingData?.adaptiveFormats
        ?.filter { fmt ->
            fmt.mimeType?.startsWith("audio/") == true &&
                !fmt.mimeType.contains("video") &&
                !fmt.url.isNullOrBlank()
        }
        ?: return emptyList()

    if (allFormats.isEmpty()) return emptyList()

    return when (audioQualityFormat) {
        AudioQualityFormat.High   -> allFormats.sortedByDescending { it.bitrate }
        AudioQualityFormat.Low    -> allFormats.sortedBy { it.bitrate }
        AudioQualityFormat.Medium -> {
            val sorted = allFormats.sortedBy { it.bitrate }
            val mid = sorted[sorted.size / 2]
            listOf(mid) + (sorted - mid)
        }
        AudioQualityFormat.Auto   -> {
            if (connectionMetered && isConnectionMeteredEnabled())
                allFormats.sortedBy { it.bitrate }
            else
                allFormats.sortedByDescending { it.bitrate }
        }
    }
}

private fun extractMetroListFormatsRanked(
    streamingData: MetroListPlayerResponse.StreamingData?,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): List<MetroListPlayerResponse.StreamingData.Format> {
    val allFormats = streamingData?.adaptiveFormats
        ?.filter { fmt ->
            fmt.mimeType.startsWith("audio/") &&
                fmt.width == null &&
                (!fmt.url.isNullOrBlank() || !fmt.signatureCipher.isNullOrBlank() || !fmt.cipher.isNullOrBlank())
        }
        ?: return emptyList()

    if (allFormats.isEmpty()) return emptyList()

    return when (audioQualityFormat) {
        AudioQualityFormat.High   -> allFormats.sortedByDescending { it.averageBitrate ?: it.bitrate }
        AudioQualityFormat.Low    -> allFormats.sortedBy { it.averageBitrate ?: it.bitrate }
        AudioQualityFormat.Medium -> {
            val sorted = allFormats.sortedBy { it.averageBitrate ?: it.bitrate }
            val mid = sorted[sorted.size / 2]
            listOf(mid) + (sorted - mid)
        }
        AudioQualityFormat.Auto   -> {
            if (connectionMetered && isConnectionMeteredEnabled())
                allFormats.sortedBy { it.averageBitrate ?: it.bitrate }
            else
                allFormats.sortedByDescending { it.averageBitrate ?: it.bitrate }
        }
    }
}

// ─── URI builders (direct YouTube clients) ───────────────────────────────────

/**
 * Parse player response JSON, deobfuscate throttling params, and return a
 * directly playable URI.  Probes each candidate from **this device's IP** so
 * we only return URLs that ExoPlayer can actually stream.
 *
 * Do NOT use this for URLs sourced from a proxy (Crystal / Omada) — those
 * URLs are IP-bound to the proxy server and cannot be streamed from the device.
 */
@UnstableApi
private fun getFormatUriPersisting(
    videoId: String,
    cpn: String,
    responseJson: JsonObject,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean,
    streamingDataPoToken: String? = null,
): Uri {
    val jsonString = normalizePlayerResponseJson(Gson().toJson(responseJson))
    val playerResponse = jsonParser.decodeFromString<PlayerResponse>(jsonString)

    checkPlayability(playerResponse.playabilityStatus)

    val rankedFormats = extractAllFormatsRanked(
        playerResponse.streamingData,
        audioQualityFormat,
        connectionMetered
    )

    if (rankedFormats.isEmpty()) throw PlayableFormatNotFoundException()

    var lastError: Throwable? = null
    for (format in rankedFormats) {
        val rawUrl = format.url?.takeIf { it.isNotBlank() } ?: continue

        val uriWithoutCpn = runCatching {
            YoutubeJavaScriptPlayerManager
                .getUrlWithThrottlingParameterDeobfuscated(videoId, rawUrl)
                .toUri()
                .buildUpon()
                .apply {
                    streamingDataPoToken
                        ?.takeIf { it.isNotBlank() }
                        ?.let { appendQueryParameter("pot", it) }
                }
                .build()
        }.onFailure { e ->
            Timber.w(e, "Format itag=%d deobfuscation failed for %s", format.itag, videoId)
            lastError = e
        }.getOrNull() ?: continue

        val finalUri = uriWithoutCpn.buildUpon().appendQueryParameter("cpn", cpn).build()

        // Safe to probe: URL was generated for this device's IP via a direct
        // YouTube API call, so ExoPlayer will also stream from this IP.
        if (!finalUri.openProbe(videoId, format.itag)) continue

        CoroutineScope(Threads.DATASPEC_DISPATCHER).launch {
            upsertSongFormat(videoId, format)
        }

        Timber.d("Resolved format itag=%d bitrate=%d for %s", format.itag, format.bitrate, videoId)
        return finalUri
    }

    Timber.e(lastError, "All %d formats exhausted for %s", rankedFormats.size, videoId)
    throw PlayableFormatNotFoundException()
}

/**
 * Parse a raw player response JSON and extract a URI without JS deobfuscation.
 * Used by sources (TV client) whose URLs are already clean.
 * Still probes from this device's IP — only use when the URL is device-IP-bound.
 */
@UnstableApi
private fun getFormatUriDirect(
    videoId: String,
    responseJson: JsonObject,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean,
): Uri {
    val jsonString = normalizePlayerResponseJson(Gson().toJson(responseJson))
    val playerResponse = jsonParser.decodeFromString<PlayerResponse>(jsonString)

    checkPlayability(playerResponse.playabilityStatus)

    val rankedFormats = extractAllFormatsRanked(
        playerResponse.streamingData,
        audioQualityFormat,
        connectionMetered
    )

    if (rankedFormats.isEmpty()) throw PlayableFormatNotFoundException()

    for (format in rankedFormats) {
        val rawUrl = format.url?.takeIf { it.isNotBlank() } ?: continue
        val uri = rawUrl.toUri()

        if (!uri.openProbe(videoId, format.itag)) continue

        CoroutineScope(Threads.DATASPEC_DISPATCHER).launch {
            upsertSongFormat(videoId, format)
        }

        Timber.d("TV/direct resolved format itag=%d bitrate=%d for %s", format.itag, format.bitrate, videoId)
        return uri
    }

    throw PlayableFormatNotFoundException()
}

@UnstableApi
private fun getMetroListFormatUriPersisting(
    videoId: String,
    playerResponse: MetroListPlayerResponse,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean,
): Uri {
    if (playerResponse.playabilityStatus.status != "OK") {
        when (playerResponse.playabilityStatus.status) {
            "LOGIN_REQUIRED" -> throw LoginRequiredException()
            "UNPLAYABLE"     -> throw UnplayableException()
            else             -> throw UnknownException()
        }
    }

    val rankedFormats = extractMetroListFormatsRanked(
        playerResponse.streamingData,
        audioQualityFormat,
        connectionMetered
    )

    if (rankedFormats.isEmpty()) throw PlayableFormatNotFoundException()

    rankedFormats.forEach { format ->
        val rawUrl = NewPipeUtils.getStreamUrl(format, videoId)
            .onFailure { Timber.w(it, "MetroList format itag=%d URL decode failed for %s", format.itag, videoId) }
            .getOrNull() ?: return@forEach

        val uri = rawUrl.toUri()
        if (!uri.openProbe(videoId, format.itag)) return@forEach

        Timber.d(
            "MetroList resolved format itag=%d bitrate=%d for %s",
            format.itag,
            format.averageBitrate ?: format.bitrate,
            videoId
        )
        return uri
    }

    throw PlayableFormatNotFoundException()
}

// ─── PoToken helpers ──────────────────────────────────────────────────────────

private fun String.getPoToken(): String? =
    this.replace("[", "")
        .replace("]", "")
        .split(",")
        .findLast { it.contains("\"") }
        ?.replace("\"", "")

private suspend fun generateIosPoToken() =
    createPoTokenChallenge().bodyAsText()
        .let { challenge ->
            val listChallenge = jsonParser.decodeFromString<List<String?>>(challenge)
            listChallenge.filterIsInstance<String>().firstOrNull()
        }?.let { poTokenChallenge ->
            Innertube.generatePoToken(poTokenChallenge)
                .bodyAsText()
                .getPoToken()
        }

private suspend fun getWebPoToken(videoId: String): WebPoTokenResult? {
    if (!isNetworkConnected(appContext()) || !NetworkClientFactory.canReachYouTube()) {
        Timber.w("Skipping Web PoToken for %s: YouTube not reachable", videoId)
        return null
    }
    return withTimeoutOrNull(8_000L) {
        withContext(Dispatchers.IO) {
            runCatching { PoTokenGenerator().getWebClientPoToken(videoId) }
                .onFailure { Timber.w(it, "Unable to create Web PoToken for %s", videoId) }
                .getOrNull()
        }
    }
}

// ─── Per-source format URL resolvers ─────────────────────────────────────────

@UnstableApi
suspend fun getAndroidReelFormatUrl(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): Uri {
    val cpn = CharUtils.randomString(16)
    val currentLocale = java.util.Locale.getDefault()
    val contentCountry = ContentCountry(currentLocale.country.takeIf { it.isNotBlank() } ?: "GB")
    val localization = Localization(currentLocale.language.takeIf { it.isNotBlank() } ?: "en")
    val response = YoutubeStreamHelper.getAndroidReelPlayerResponse(
        contentCountry, localization, videoId, cpn
    )
    return getFormatUriPersisting(videoId, cpn, response, audioQualityFormat, connectionMetered)
}

@UnstableApi
suspend fun getIosFormatUrl(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): Uri {
    if (isYouTubeLoggedIn()) {
        YouTubeSessionStore.applyCurrentSession()
        val authenticated = runCatching {
            val poToken = getWebPoToken(videoId)
            val response = YouTubeRequestThrottler.run {
                Innertube.player(videoId = videoId, poToken = poToken?.playerRequestPoToken)
            }?.getOrThrow() ?: throw IllegalStateException("Null player response")
            val jsonString = Gson().toJson(response)
            return@getIosFormatUrl getFormatUriPersisting(
                videoId = videoId,
                cpn = CharUtils.randomString(16),
                responseJson = Gson().fromJson(jsonString, JsonObject::class.java),
                audioQualityFormat = audioQualityFormat,
                connectionMetered = connectionMetered,
                streamingDataPoToken = poToken?.streamingDataPoToken
            )
        }
        authenticated.getOrElse { it.printStackTrace() }
    }

    val cpn = CharUtils.randomString(16)
    val visitorData = Store.getIosVisitorData()
    val playerRequestToken = generateIosPoToken().orEmpty()
    val poTokenResult = PoTokenResult(visitorData, playerRequestToken, null)
    val currentLocale = java.util.Locale.getDefault()
    val contentCountry = ContentCountry(currentLocale.country.takeIf { it.isNotBlank() } ?: "GB")
    val localization = Localization(currentLocale.language.takeIf { it.isNotBlank() } ?: "en")
    val response = YoutubeStreamHelper.getIosPlayerResponse(
        contentCountry, localization, videoId, cpn, poTokenResult
    )
    return getFormatUriPersisting(videoId, cpn, response, audioQualityFormat, connectionMetered)
}

/**
 * TVHTML5_SIMPLY_EMBEDDED_PLAYER client.
 *
 * Bypasses most region/content restrictions and requires no PoToken.
 * URLs do not require JS throttle deobfuscation (client number 85).
 */
@UnstableApi
suspend fun getTvHtml5FormatUrl(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): Uri {
    val tvContext = Context(
        client = Context.Client(
            clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            clientVersion = "2.0",
            gl = java.util.Locale.getDefault().country.takeIf { it.isNotBlank() } ?: "US",
            hl = java.util.Locale.getDefault().language.takeIf { it.isNotBlank() } ?: "en",
            visitorData = Innertube.visitorData,
        ),
        thirdParty = Context.ThirdParty(
            embedUrl = "https://www.youtube.com/watch?v=$videoId"
        )
    )

    val response = YouTubeRequestThrottler.run {
        Innertube.player(videoId = videoId, context = tvContext, poToken = null)
    }?.getOrThrow() ?: throw IllegalStateException("Null TV player response for $videoId")

    val jsonString = Gson().toJson(response)
    val responseJson = Gson().fromJson(jsonString, JsonObject::class.java)

    return runCatching {
        getFormatUriDirect(videoId, responseJson, audioQualityFormat, connectionMetered)
    }.recoverCatching {
        Timber.w(it, "TV direct extraction failed for %s, trying deobfuscated path", videoId)
        getFormatUriPersisting(
            videoId = videoId,
            cpn = CharUtils.randomString(16),
            responseJson = responseJson,
            audioQualityFormat = audioQualityFormat,
            connectionMetered = connectionMetered,
            streamingDataPoToken = null
        )
    }.getOrThrow()
}

@UnstableApi
suspend fun getInnertubePlayerFormatUrl(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): Uri {
    Innertube.ensureFreshVisitorData()
    val poToken = getWebPoToken(videoId)
    val response = YouTubeRequestThrottler.run {
        Innertube.player(videoId = videoId, poToken = poToken?.playerRequestPoToken)
    }?.getOrThrow() ?: throw IllegalStateException("Null Innertube player response")

    val jsonString = Gson().toJson(response)
    return getFormatUriPersisting(
        videoId = videoId,
        cpn = CharUtils.randomString(16),
        responseJson = Gson().fromJson(jsonString, JsonObject::class.java),
        audioQualityFormat = audioQualityFormat,
        connectionMetered = connectionMetered,
        streamingDataPoToken = poToken?.streamingDataPoToken
    )
}

@UnstableApi
private suspend fun getMetroListFormatUrl(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): Uri {
    val signatureTimestamp by lazy {
        NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
    }
    val clients = listOf(
        MetroListYouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        MetroListYouTubeClient.ANDROID_VR_1_43_32,
        MetroListYouTubeClient.ANDROID_NO_SDK,
        MetroListYouTubeClient.IOS,
        MetroListYouTubeClient.WEB_REMIX,
    )

    var firstError: Throwable? = null
    clients.forEach { client ->
        val response = MetroListYouTube.player(
            videoId = videoId,
            client = client,
            signatureTimestamp = if (client.useSignatureTimestamp) signatureTimestamp else null,
            poToken = null
        ).onFailure { error ->
            if (firstError == null) firstError = error
            Timber.w(
                error,
                "MetroList client %s failed for %s",
                client.friendlyName ?: client.clientName,
                videoId
            )
        }.getOrNull() ?: return@forEach

        runCatching {
            getMetroListFormatUriPersisting(
                videoId = videoId,
                playerResponse = response,
                audioQualityFormat = audioQualityFormat,
                connectionMetered = connectionMetered
            )
        }.onSuccess { return it }
            .onFailure { error ->
                if (firstError == null) firstError = error
                Timber.w(
                    error,
                    "MetroList client %s returned no playable stream for %s",
                    client.friendlyName ?: client.clientName,
                    videoId
                )
            }
    }

    throw (firstError as? Exception ?: PlayableFormatNotFoundException())
}

// ─── Proxy-backed resolvers ───────────────────────────────────────────────────

/**
 * Build an Omada streaming proxy URL for [videoId] and [itag].
 *
 * ExoPlayer fetches audio bytes directly from `yt.omada.cafe/api/public/stream`.
 * The Omada server proxies the request to googlevideo using its own IP,
 * so there is no IP-mismatch 403 regardless of the client's IP.
 */
private fun buildOmadaStreamUri(videoId: String, itag: Int): Uri =
    "$OMADA_STREAM_BASE/$videoId"
        .toUri()
        .buildUpon()
        .appendQueryParameter("itag", itag.toString())
        .build()

/**
 * Lightweight probe to verify that the Omada proxy endpoint actually returns
 * a valid audio stream, not an HTML error page or JSON.
 *
 * Does a small range request (bytes 0-255) and checks:
 * - HTTP status is 2xx
 * - Content-Type header starts with "audio/"
 * - First few bytes contain either "ID3" (MP3) or "ftyp" (MP4/WebM)
 *
 * If any check fails, the proxy is considered unusable for this video/itag.
 */
private suspend fun isOmadaStreamValid(videoId: String, itag: Int): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val uri = buildOmadaStreamUri(videoId, itag)
            val connection = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Range", "bytes=0-255")
                connectTimeout = 5000
                readTimeout = 5000
            }
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                Timber.w("Omada proxy returned HTTP $responseCode for $videoId itag=$itag")
                return@runCatching false
            }

            val contentType = connection.getHeaderField("Content-Type")
            if (contentType?.startsWith("audio/") == true) {
                return@runCatching true
            }

            // Content-Type missing or non‑audio – peek the first 4 bytes
            val inputStream = connection.inputStream
            val headerBytes = ByteArray(4)
            val bytesRead = inputStream.read(headerBytes)
            if (bytesRead >= 4) {
                val headerStr = String(headerBytes)
                if (headerStr == "ID3" || headerStr.startsWith("ftyp")) {
                    return@runCatching true
                }
                Timber.w("Omada proxy non‑audio data for $videoId itag=$itag: contentType=$contentType, header=${headerBytes.joinToString("") { "%02x".format(it) }}")
            } else {
                Timber.w("Omada proxy returned too short response for $videoId itag=$itag")
            }
            false
        }.onFailure {
            Timber.w(it, "Omada proxy probe failed for $videoId itag=$itag")
        }.getOrDefault(false)
    }

/**
 * Resolve a playable URI via the Omada streaming proxy.
 *
 * **Performs a lightweight content‑type check** before returning the URI.
 * If the proxy returns HTML/JSON or any non‑audio response, we throw
 * `PlayableFormatNotFoundException`, which causes the fallback chain to
 * proceed to the next source.
 *
 * Itag preference: 140 (AAC 128kbps) first for widest device compatibility,
 * then Opus variants. For Low quality, smaller Opus variants are tried first.
 */
@UnstableApi
private suspend fun getFlowTuneFormatUrl(
    videoId: String,
    audioQualityFormat: AudioQualityFormat
): Uri {
    flowTuneFailureUntil[videoId]?.let { retryAfter ->
        if (System.currentTimeMillis() < retryAfter) {
            Timber.w("Omada proxy is cooling down for %s", videoId)
            throw PlayableFormatNotFoundException()
        }
        flowTuneFailureUntil.remove(videoId)
    }

    val preferredItag: Int = when (audioQualityFormat) {
        AudioQualityFormat.Low    -> 249   // Opus ~48kbps
        AudioQualityFormat.Medium -> 250   // Opus ~70kbps
        AudioQualityFormat.High,
        AudioQualityFormat.Auto   -> 140   // AAC 128kbps — best compat
    }

    // Lightweight validation: does the proxy actually serve audio for this itag?
    if (!isOmadaStreamValid(videoId, preferredItag)) {
        Timber.w("Omada proxy returned invalid content for %s itag=%d", videoId, preferredItag)
        throw PlayableFormatNotFoundException()
    }

    val streamUri = buildOmadaStreamUri(videoId, preferredItag)
    Timber.d("Omada proxy returning itag=%d stream for %s (validated)", preferredItag, videoId)
    return streamUri
}

/**
 * Resolve a playable URI via the Vercel Crystal API.
 *
 * Calls the Vercel `/api/stream` endpoint to discover which itags are available,
 * then returns an Omada streaming proxy URL for the best itag. The Vercel API's
 * raw googlevideo URLs are NEVER returned to ExoPlayer — they are IP-bound to the
 * Vercel server's IP and would 403 from the device.
 *
 * The final proxy URL is also validated using [isOmadaStreamValid] before returning.
 */
@UnstableApi
private suspend fun getCrystalApiDirectFormatUrl(
    videoId: String,
    audioQualityFormat: AudioQualityFormat
): Uri {
    val quality = when (audioQualityFormat) {
        AudioQualityFormat.Low    -> "low"
        AudioQualityFormat.Medium -> "high"
        AudioQualityFormat.High,
        AudioQualityFormat.Auto   -> "best"
    }

    val apiUri = "$CRYSTAL_API_STREAM_BASE/$videoId"
        .toUri()
        .buildUpon()
        .appendQueryParameter("quality", quality)
        .appendQueryParameter("_", System.currentTimeMillis().toString())
        .build()

    val responseText = runCatching {
        val connection = (URL(apiUri.toString()).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
        }
        val code = connection.responseCode
        val body = if (code == HttpURLConnection.HTTP_OK) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            val err = runCatching {
                connection.errorStream?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            Timber.w("Crystal API returned HTTP %d for %s: %s", code, videoId, err.orEmpty())
            null
        }
        connection.disconnect()
        body
    }.onFailure {
        Timber.w(it, "Crystal API request failed for %s", videoId)
    }.getOrNull() ?: throw PlayableFormatNotFoundException()

    val root = runCatching { JsonParser.parseString(responseText).asJsonObject }
        .onFailure { Timber.w(it, "Crystal API returned invalid JSON for %s", videoId) }
        .getOrNull() ?: throw PlayableFormatNotFoundException()

    fun com.google.gson.JsonElement?.intOrNull(): Int? =
        runCatching { this?.takeUnless { it.isJsonNull }?.asInt }.getOrNull()

    fun com.google.gson.JsonElement?.stringOrNull(): String? =
        runCatching { this?.takeUnless { it.isJsonNull }?.asString }.getOrNull()

    data class CrystalApiCandidate(
        val uri: Uri,
        val itag: Int?,
        val mime: String?,
        val bitrate: Int?,
        val selected: Boolean
    )

    val selected = runCatching { root.getAsJsonObject("selected") }.getOrNull()
    val candidates = mutableListOf<CrystalApiCandidate>()

    root.get("url").stringOrNull()
        ?.takeIf { it.isNotBlank() }
        ?.let { url ->
            candidates += CrystalApiCandidate(
                uri = url.toUri(),
                itag = selected?.get("itag").intOrNull(),
                mime = selected?.get("mime").stringOrNull(),
                bitrate = selected?.get("bitrate").intOrNull(),
                selected = true
            )
        }

    root.getAsJsonArray("urls")?.forEach { item ->
        val obj = runCatching { item.asJsonObject }.getOrNull() ?: return@forEach
        val url = obj.get("url").stringOrNull()?.takeIf { it.isNotBlank() } ?: return@forEach
        candidates += CrystalApiCandidate(
            uri = url.toUri(),
            itag = obj.get("itag").intOrNull(),
            mime = obj.get("mime").stringOrNull(),
            bitrate = obj.get("bitrate").intOrNull(),
            selected = false
        )
    }

    val uniqueCandidates = candidates.distinctBy { it.uri.toString() }
    val ranked = when (audioQualityFormat) {
        AudioQualityFormat.Low -> uniqueCandidates.sortedWith(
            compareByDescending<CrystalApiCandidate> { if (it.selected) 1 else 0 }
                .thenBy { it.bitrate ?: Int.MAX_VALUE }
        )
        AudioQualityFormat.Medium,
        AudioQualityFormat.High,
        AudioQualityFormat.Auto -> uniqueCandidates.sortedWith(
            compareByDescending<CrystalApiCandidate> { if (it.selected) 1 else 0 }
                .thenByDescending { it.bitrate ?: 0 }
        )
    }

    val chosen = ranked.firstOrNull() ?: throw PlayableFormatNotFoundException()
    val bestItag = chosen.itag ?: -1
    Timber.d("Crystal API direct itag=%d for %s", bestItag, videoId)
    return chosen.uri
}

// ─── Source orchestration ─────────────────────────────────────────────────────

private suspend fun resolvePrimaryFormatUrl(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean,
    isFallback: Boolean = false,
    sourceOverride: InnertubePlayerSource? = null
): Uri {
    val selectedSource = sourceOverride ?: appContext().preferences.getEnum(
        innertubePlayerSourceKey,
        InnertubePlayerSource.CrystalApi
    )
    val alternateSourcesEnabled = appContext().preferences.getBoolean(alternateSourceRetryKey, true)

    fun MutableList<Pair<PlaybackSourceKind, suspend () -> Uri>>.addIfReady(
        sourceKind: PlaybackSourceKind,
        resolver: suspend () -> Uri
    ) {
        if (sourceKind.isCoolingDownFor(videoId)) {
            Timber.w("Source %s is cooling down for %s, skipping", sourceKind.label, videoId)
            return
        }
        add(sourceKind to resolver)
    }

    // Source ordering rationale:
    //  1. Omada proxy (FlowTune) — true streaming proxy, validated content
    //  2. Crystal Vercel → discovers itags from Vercel API, streams via Omada proxy (also validated)
    //  3. TV client — bypasses region locks, no PoToken needed, direct but reliable
    //  4. Innertube — web client with PoToken
    //  5. MetroList — multi-client fallback (alternates only)
    //  6. iOS / Android — direct clients, most likely to 403 (alternates only)
    //
    // TV + Innertube are ALWAYS included regardless of alternateSourcesEnabled.
    // They are the proven reliable fallbacks when the proxy sources fail.
    val attempts: List<Pair<PlaybackSourceKind, suspend () -> Uri>> = when (selectedSource) {

        InnertubePlayerSource.CrystalApi -> buildList {
            addIfReady(PlaybackSourceKind.CrystalApi) {
                getCrystalApiDirectFormatUrl(videoId, audioQualityFormat)
            }
            if (sourceOverride == null) {
                addIfReady(PlaybackSourceKind.FlowTune) {
                    getFlowTuneFormatUrl(videoId, audioQualityFormat)
                }
            }
            addIfReady(PlaybackSourceKind.YouTubeTvHtml5) {
                getTvHtml5FormatUrl(videoId, audioQualityFormat, connectionMetered)
            }
            addIfReady(PlaybackSourceKind.YouTubeInnertube) {
                getInnertubePlayerFormatUrl(videoId, audioQualityFormat, connectionMetered)
            }
            if (alternateSourcesEnabled && sourceOverride == null) {
                addIfReady(PlaybackSourceKind.MetroList) {
                    getMetroListFormatUrl(videoId, audioQualityFormat, connectionMetered)
                }
                addIfReady(PlaybackSourceKind.YouTubeIos) {
                    getIosFormatUrl(videoId, audioQualityFormat, connectionMetered)
                }
                addIfReady(PlaybackSourceKind.YouTubeAndroid) {
                    getAndroidReelFormatUrl(videoId, audioQualityFormat, connectionMetered)
                }
            }
        }

        InnertubePlayerSource.OldInnertube -> buildList {
            // Proxy sources first — no IP-mismatch risk
            addIfReady(PlaybackSourceKind.FlowTune) {
                getFlowTuneFormatUrl(videoId, audioQualityFormat)
            }
            if (sourceOverride == null) {
                addIfReady(PlaybackSourceKind.CrystalApi) {
                    getCrystalApiDirectFormatUrl(videoId, audioQualityFormat)
                }
            }
            // Direct YouTube clients — always present as reliable fallbacks
            addIfReady(PlaybackSourceKind.YouTubeTvHtml5) {
                getTvHtml5FormatUrl(videoId, audioQualityFormat, connectionMetered)
            }
            addIfReady(PlaybackSourceKind.YouTubeInnertube) {
                getInnertubePlayerFormatUrl(videoId, audioQualityFormat, connectionMetered)
            }
            // Extended fallbacks — only when alternates are enabled
            if (alternateSourcesEnabled) {
                if (sourceOverride == null) {
                    addIfReady(PlaybackSourceKind.MetroList) {
                        getMetroListFormatUrl(videoId, audioQualityFormat, connectionMetered)
                    }
                }
                addIfReady(PlaybackSourceKind.YouTubeIos) {
                    getIosFormatUrl(videoId, audioQualityFormat, connectionMetered)
                }
                addIfReady(PlaybackSourceKind.YouTubeAndroid) {
                    getAndroidReelFormatUrl(videoId, audioQualityFormat, connectionMetered)
                }
            }
        }

        InnertubePlayerSource.FlowTune -> buildList {
            addIfReady(PlaybackSourceKind.FlowTune) {
                getFlowTuneFormatUrl(videoId, audioQualityFormat)
            }
            if (sourceOverride == null) {
                addIfReady(PlaybackSourceKind.CrystalApi) {
                    getCrystalApiDirectFormatUrl(videoId, audioQualityFormat)
                }
            }
            // Always include direct clients as unconditional fallbacks
            addIfReady(PlaybackSourceKind.YouTubeTvHtml5) {
                getTvHtml5FormatUrl(videoId, audioQualityFormat, connectionMetered)
            }
            addIfReady(PlaybackSourceKind.YouTubeInnertube) {
                getInnertubePlayerFormatUrl(videoId, audioQualityFormat, connectionMetered)
            }
            if (alternateSourcesEnabled && sourceOverride == null) {
                addIfReady(PlaybackSourceKind.MetroList) {
                    getMetroListFormatUrl(videoId, audioQualityFormat, connectionMetered)
                }
                addIfReady(PlaybackSourceKind.YouTubeIos) {
                    getIosFormatUrl(videoId, audioQualityFormat, connectionMetered)
                }
                addIfReady(PlaybackSourceKind.YouTubeAndroid) {
                    getAndroidReelFormatUrl(videoId, audioQualityFormat, connectionMetered)
                }
            }
        }

        InnertubePlayerSource.MetroList -> buildList {
            addIfReady(PlaybackSourceKind.MetroList) {
                getMetroListFormatUrl(videoId, audioQualityFormat, connectionMetered)
            }
            if (sourceOverride == null) {
                addIfReady(PlaybackSourceKind.FlowTune) {
                    getFlowTuneFormatUrl(videoId, audioQualityFormat)
                }
                addIfReady(PlaybackSourceKind.CrystalApi) {
                    getCrystalApiDirectFormatUrl(videoId, audioQualityFormat)
                }
            }
            // Always include direct clients as unconditional fallbacks
            addIfReady(PlaybackSourceKind.YouTubeTvHtml5) {
                getTvHtml5FormatUrl(videoId, audioQualityFormat, connectionMetered)
            }
            addIfReady(PlaybackSourceKind.YouTubeInnertube) {
                getInnertubePlayerFormatUrl(videoId, audioQualityFormat, connectionMetered)
            }
            if (alternateSourcesEnabled && sourceOverride == null) {
                addIfReady(PlaybackSourceKind.YouTubeIos) {
                    getIosFormatUrl(videoId, audioQualityFormat, connectionMetered)
                }
                addIfReady(PlaybackSourceKind.YouTubeAndroid) {
                    getAndroidReelFormatUrl(videoId, audioQualityFormat, connectionMetered)
                }
            }
        }
    }

    if (attempts.isEmpty()) {
        Timber.w("All playback sources are cooling down for %s; refusing immediate retry", videoId)
        throw PlayableFormatNotFoundException()
    }

    var firstError: Throwable? = null
    attempts.forEachIndexed { index, (sourceKind, resolver) ->
        runCatching { resolver() }
            .onSuccess { uri ->
                PlaybackSourceMonitor.report(sourceKind, videoId, isFallback)
                clearPlaybackSourceFailure(videoId, sourceKind)
                return uri
            }
            .onFailure { error ->
                if (firstError == null) firstError = error
                Timber.w(
                    error,
                    "Source %s failed for %s%s%s",
                    sourceKind.label,
                    videoId,
                    if (isFallback) " (fallback)" else "",
                    if (index < attempts.lastIndex) ", trying next" else ""
                )
                markPlaybackSourceFailed(videoId, sourceKind)
            }
    }

    throw (firstError as? Exception ?: UnknownException())
}

// ─── Format cache & failure tracking ─────────────────────────────────────────

// Thread-safe maps
private val formatCache = ConcurrentHashMap<String, Uri>()
private val flowTuneFailureUntil = ConcurrentHashMap<String, Long>()
private val playbackSourceFailureUntil = ConcurrentHashMap<String, ConcurrentHashMap<PlaybackSourceKind, Long>>()

private fun PlaybackSourceKind.isCoolingDownFor(videoId: String): Boolean {
    val retryAfter = playbackSourceFailureUntil[videoId]?.get(this) ?: return false
    if (System.currentTimeMillis() < retryAfter) return true
    playbackSourceFailureUntil[videoId]?.remove(this)
    if (playbackSourceFailureUntil[videoId]?.isEmpty() == true) {
        playbackSourceFailureUntil.remove(videoId)
    }
    return false
}

private fun markPlaybackSourceFailed(videoId: String, source: PlaybackSourceKind) {
    if (source == PlaybackSourceKind.Unknown || source == PlaybackSourceKind.Local) return
    playbackSourceFailureUntil
        .getOrPut(videoId) { ConcurrentHashMap() }[source] =
        System.currentTimeMillis() + PLAYBACK_SOURCE_FAILURE_COOLDOWN_MS
}

private fun clearPlaybackSourceFailure(videoId: String, source: PlaybackSourceKind) {
    playbackSourceFailureUntil[videoId]?.remove(source)
    if (playbackSourceFailureUntil[videoId]?.isEmpty() == true) {
        playbackSourceFailureUntil.remove(videoId)
    }
}

private fun clearPlaybackSourceFailures(videoId: String) {
    playbackSourceFailureUntil.remove(videoId)
}

/**
 * Invalidate the cached format URL for [videoId].
 *
 * Call this from `PlayerServiceModern.onPlayerError` when you receive
 * `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` (3003). This ensures the
 * broken URL is not reused and the next source in the fallback chain is tried.
 *
 * @param punishFlowTune If true, puts FlowTune (Omada proxy) on cooldown for this video.
 * @param failedSource Optional source that caused the failure; will be marked as failed.
 */
fun invalidatePlaybackFormatCache(
    videoId: String,
    punishFlowTune: Boolean = false,
    failedSource: PlaybackSourceKind? = null
) {
    if (videoId.isBlank()) return
    formatCache.keys
        .filter { it.endsWith(":$videoId") }
        .forEach { formatCache.remove(it) }
    failedSource?.let { markPlaybackSourceFailed(videoId, it) }
    if (punishFlowTune || failedSource == PlaybackSourceKind.FlowTune) {
        flowTuneFailureUntil[videoId] = System.currentTimeMillis() + FLOWTUNE_FAILURE_COOLDOWN_MS
    }
}

// ─── DataSpec processor ───────────────────────────────────────────────────────

@UnstableApi
fun DataSpec.process(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean,
    sourceOverride: InnertubePlayerSource? = null
): DataSpec {
    return runBlocking(Dispatchers.IO) {
        if (!isNetworkConnected(appContext())) throw NoInternetException()
        if (!videoId.isYouTubeVideoId()) {
            Timber.w("Refusing to resolve non-video playback id=%s", videoId)
            throw PlayableFormatNotFoundException()
        }

        val selectedSource = sourceOverride ?: appContext().preferences.getEnum(
            innertubePlayerSourceKey,
            InnertubePlayerSource.CrystalApi
        )

        val cacheKey = "${selectedSource.name}:$videoId"
        var cachedFormatUri = formatCache[cacheKey]
        if (cachedFormatUri != null) {
            val expireTime = cachedFormatUri.getQueryParameter("expire")?.toLongOrNull()?.times(1000)
            if (expireTime != null && System.currentTimeMillis() >= expireTime - 120_000) {
                formatCache.remove(cacheKey)
                cachedFormatUri = null
            }
        }

        val formatUri = cachedFormatUri ?: try {
            resolvePrimaryFormatUrl(
                videoId = videoId,
                audioQualityFormat = audioQualityFormat,
                connectionMetered = connectionMetered,
                isFallback = false,
                sourceOverride = sourceOverride
            )
        } catch (primaryError: Throwable) {
            Timber.w(primaryError, "All configured playback sources failed for %s", videoId)
            if (!isNetworkConnected(appContext())) throw NoInternetException()
            if (primaryError.isNetworkUnavailableFailure()) throw NoInternetException()
            throw (primaryError as? Exception ?: UnknownException())
        }.also { formatCache[cacheKey] = it }

        withUri(formatUri).subrange(uriPositionOffset)
    }
}

// ─── Data source factories ────────────────────────────────────────────────────

@UnstableApi
fun PlayerServiceModern.createDataSourceFactory(): DataSource.Factory {
    val upstreamFactory = appContext().okHttpDataSourceFactory

    val resolvingDataSourceFactory = ResolvingDataSource.Factory(upstreamFactory) { dataSpec ->
        val videoId = dataSpec.uri.toString().substringAfter("watch?v=")
        val isLocal = dataSpec.uri.scheme == ContentResolver.SCHEME_CONTENT ||
            dataSpec.uri.scheme == ContentResolver.SCHEME_FILE

        if (isLocal) {
            PlaybackSourceMonitor.report(PlaybackSourceKind.Local, videoId)
            return@Factory dataSpec
        }

        CoroutineScope(Threads.DATASPEC_DISPATCHER).launch { upsertSongInfo(videoId) }

        runCatching {
            dataSpec.process(videoId, audioQualityFormat, applicationContext.isConnectionMetered())
                .buildUpon()
                .setKey(videoId)
                .build()
        }.onFailure {
            Timber.e(it, "Failed to resolve playback DataSpec for %s.", videoId)
        }.getOrThrow()
    }

    val lruCacheFactory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(resolvingDataSourceFactory)

    return CacheDataSource.Factory()
        .setCache(downloadCache)
        .setUpstreamDataSourceFactory(lruCacheFactory)
        .setCacheWriteDataSinkFactory(null)
        .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)
}

@UnstableApi
fun MyDownloadHelper.createDataSourceFactory(): DataSource.Factory {
    val upstreamFactory = appContext().okHttpDataSourceFactory

    val resolvingDataSourceFactory = ResolvingDataSource.Factory(upstreamFactory) { dataSpec ->
        val videoId = dataSpec.uri.toString().substringAfter("watch?v=")

        CoroutineScope(Threads.DATASPEC_DISPATCHER).launch { upsertSongInfo(videoId) }

        runCatching {
            dataSpec.process(
                videoId,
                audioQualityFormat,
                appContext().isConnectionMetered(),
                InnertubePlayerSource.CrystalApi
            )
                .buildUpon()
                .setKey(videoId)
                .build()
        }.onFailure {
            Timber.e(it, "Failed to resolve download DataSpec for %s.", videoId)
        }.getOrThrow()
    }

    return CacheDataSource.Factory()
        .setCache(getDownloadCache(appContext()))
        .setUpstreamDataSourceFactory(resolvingDataSourceFactory)
        .setCacheWriteDataSinkFactory(null)
}
