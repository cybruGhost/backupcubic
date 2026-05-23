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
import app.kreate.android.network.innertube.Store
import app.kreate.android.utils.CharUtils
import com.google.gson.Gson
import com.grack.nanojson.JsonObject
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import it.fast4x.environment.Environment
import it.fast4x.environment.models.Context as EnvironmentContext
import it.fast4x.environment.models.PlayerResponse as EnvironmentPlayerResponse
import it.fast4x.innertube.Innertube
import app.it.fast4x.rimusic.models.Song
import it.fast4x.innertube.Innertube.createPoTokenChallenge
import it.fast4x.innertube.Innertube.SearchFilter
import it.fast4x.innertube.models.PlayerResponse
import it.fast4x.innertube.models.bodies.NextBody
import it.fast4x.innertube.models.bodies.SearchBody
import it.fast4x.innertube.requests.nextPage
import it.fast4x.innertube.requests.searchPage
import app.it.fast4x.rimusic.Database
import app.it.fast4x.rimusic.appContext
import app.it.fast4x.rimusic.cleanPrefix
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
import app.it.fast4x.rimusic.utils.getPipedSession
import app.it.fast4x.rimusic.utils.preferences
import com.dd3boh.outertune.utils.potoken.PoTokenGenerator
import com.dd3boh.outertune.utils.potoken.PoTokenResult as WebPoTokenResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import app.kreate.android.me.knighthat.utils.Toaster
import it.fast4x.invidious.Invidious
import it.fast4x.piped.Piped
import org.jetbrains.annotations.Blocking
import org.jetbrains.annotations.NonBlocking
import org.json.JSONArray
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.services.youtube.YoutubeStreamHelper
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import it.fast4x.innertube.requests.player
import it.fast4x.innertube.utils.from
import timber.log.Timber

private const val CHUNK_LENGTH = 512 * 1024L     // 512Kb
private val decimalThumbnailDimensionRegex =
    Regex("""("(?:height|width)"\s*:\s*)(\d+)\.0(?=[,}])""")

private fun normalizePlayerResponseJson(json: String): String =
    decimalThumbnailDimensionRegex.replace(json) { match ->
        "${match.groupValues[1]}${match.groupValues[2]}"
    }

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
        Timber.w(error, "Format itag=%s failed open probe for %s, trying next", itag, videoId)
    }.also {
        runCatching { dataSource.close() }
    }.getOrDefault(false)
}

enum class PlaybackSourceKind(val label: String) {
    Unknown("Waiting"),
    Local("Local"),
    YouTubeAndroid("YouTube"),
    YouTubeIos("YouTube iOS"),
    YouTubeInnertube("YouTube Player"),
    YouTubeEnvironment("Environment"),
    Invidious("Invidious"),
    Piped("Piped")
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

/**
 * Store id of song just added to the database.
 * This is created to reduce load to Room
 */
@set:Synchronized
private var justInserted: String = ""

/**
 * Reach out to `next` endpoint for song's information.
 */
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

/**
 * Upsert provided format to the database
 */
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

//<editor-fold defaultstate="collapsed" desc="Extractors">
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
            "UNPLAYABLE" -> throw UnplayableException()
            else -> throw UnknownException()
        }
}

/**
 * Returns all audio-only formats from StreamingData, sorted by preference for the given quality.
 * This gives us a ranked list to iterate through rather than picking just one.
 */
private fun extractAllFormatsRanked(
    streamingData: PlayerResponse.StreamingData?,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): List<PlayerResponse.StreamingData.Format> {
    val allFormats = streamingData?.adaptiveFormats
        ?.filter { fmt ->
            // Keep only audio-only formats (no video stream)
            fmt.mimeType?.startsWith("audio/") == true &&
                !fmt.mimeType.contains("video") &&
                !fmt.url.isNullOrBlank()
        }
        ?: return emptyList()

    if (allFormats.isEmpty()) return emptyList()

    // Sort so the preferred quality is at the front, others follow as fallbacks
    return when (audioQualityFormat) {
        AudioQualityFormat.High -> allFormats.sortedByDescending { it.bitrate }
        AudioQualityFormat.Low -> allFormats.sortedBy { it.bitrate }
        AudioQualityFormat.Medium -> {
            // Put medium bitrates first, extremes last
            val sorted = allFormats.sortedBy { it.bitrate }
            val midIndex = sorted.size / 2
            val mid = sorted[midIndex]
            listOf(mid) + (sorted - mid)
        }
        AudioQualityFormat.Auto -> {
            if (connectionMetered && isConnectionMeteredEnabled())
                allFormats.sortedBy { it.bitrate }        // prefer lower on metered
            else
                allFormats.sortedByDescending { it.bitrate } // prefer higher on unmetered
        }
    }
}

/**
 * Given a raw player response JSON, try each audio format URL in ranked order
 * until one resolves (i.e. the throttling deobfuscator succeeds and the URL
 * is non-blank). Returns the first working URI, or throws [PlayableFormatNotFoundException].
 *
 * This is the core of the "persist through dead links" fix: instead of picking
 * one format and immediately escalating to another provider on failure, we exhaust
 * all candidate formats from the current provider first.
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

        val resolvedUri = runCatching {
            YoutubeJavaScriptPlayerManager
                .getUrlWithThrottlingParameterDeobfuscated(videoId, rawUrl)
                .toUri()
                .buildUpon()
                .appendQueryParameter("range", "0-${format.contentLength ?: 1_000_000}")
                .appendQueryParameter("cpn", cpn)
                .apply {
                    streamingDataPoToken
                        ?.takeIf { it.isNotBlank() }
                        ?.let { appendQueryParameter("pot", it) }
                }
                .build()
        }.onFailure { e ->
            Timber.w(e, "Format itag=%d deobfuscation failed for %s, trying next", format.itag, videoId)
            lastError = e
        }.getOrNull() ?: continue

        if (!resolvedUri.openProbe(videoId, format.itag)) {
            continue
        }

        // Persist format metadata asynchronously (best-effort)
        CoroutineScope(Threads.DATASPEC_DISPATCHER).launch {
            upsertSongFormat(videoId, format)
        }

        Timber.d("Resolved format itag=%d bitrate=%d for %s", format.itag, format.bitrate, videoId)
        return resolvedUri
    }

    Timber.e(lastError, "All %d formats exhausted for %s", rankedFormats.size, videoId)
    throw PlayableFormatNotFoundException()
}

// ─── Legacy single-pick helper kept for callers that already have a JsonObject ─────────────────
// (Used by the Android-reel path which goes through YoutubeStreamHelper directly.)
@UnstableApi
private fun getFormatUrl(
    videoId: String,
    cpn: String,
    responseJson: JsonObject,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean,
): Uri = getFormatUriPersisting(videoId, cpn, responseJson, audioQualityFormat, connectionMetered)

@UnstableApi
fun getAndroidReelFormatUrl(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): Uri {
    val cpn = CharUtils.randomString(16)
    val response = YoutubeStreamHelper.getAndroidReelPlayerResponse(
        ContentCountry.DEFAULT, Localization.DEFAULT, videoId, cpn
    )
    return getFormatUrl(videoId, cpn, response, audioQualityFormat, connectionMetered)
}

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

private fun getWebPoToken(videoId: String): WebPoTokenResult? =
    runCatching { PoTokenGenerator().getWebClientPoToken(videoId) }
        .onFailure { Timber.w(it, "Unable to create Web PoToken for %s", videoId) }
        .getOrNull()

private fun syncEnvironmentSession() {
    Environment.cookie = Innertube.cookie
    Environment.visitorData = Innertube.visitorData
    Environment.dataSyncId = Innertube.dataSyncId
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
    val response = YoutubeStreamHelper.getIosPlayerResponse(
        ContentCountry.DEFAULT, Localization.DEFAULT, videoId, cpn, poTokenResult
    )
    // Use the persisting variant so we try all formats from the iOS response too
    return getFormatUriPersisting(videoId, cpn, response, audioQualityFormat, connectionMetered)
}

@UnstableApi
suspend fun getInnertubePlayerFormatUrl(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): Uri {
    val poToken = getWebPoToken(videoId)
    val response = YouTubeRequestThrottler.run {
        Innertube.player(videoId = videoId, poToken = poToken?.playerRequestPoToken)
    }?.getOrThrow() ?: throw IllegalStateException("Null Innertube player response")

    val jsonString = Gson().toJson(response)
    // Use the persisting variant — this is the path that previously gave up on the first dead URL
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
suspend fun getEnvironmentPlayerFormatUrl(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): Uri {
    syncEnvironmentSession()
    val poToken = getWebPoToken(videoId)
    val response = Environment.simpleMetadataPlayer(
        clientType = EnvironmentContext.DefaultWeb.client,
        videoId = videoId,
        playlistId = null,
        signatureTimestamp = 20110,
        poToken = poToken?.playerRequestPoToken
    ).body<EnvironmentPlayerResponse>()

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

private suspend fun resolvePrimaryFormatUrl(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean,
    isFallback: Boolean = false
): Uri {
    val selectedSource = appContext().preferences.getEnum(
        innertubePlayerSourceKey,
        InnertubePlayerSource.OldInnertube
    )
    val alternateSourcesEnabled = appContext().preferences.getBoolean(alternateSourceRetryKey, true)

    val attempts: List<Pair<PlaybackSourceKind, suspend () -> Uri>> = when (selectedSource) {
        InnertubePlayerSource.Environment -> buildList {
            add(PlaybackSourceKind.YouTubeEnvironment to {
                getEnvironmentPlayerFormatUrl(videoId, audioQualityFormat, connectionMetered)
            })
            if (alternateSourcesEnabled) {
                add(PlaybackSourceKind.YouTubeInnertube to {
                    getInnertubePlayerFormatUrl(videoId, audioQualityFormat, connectionMetered)
                })
                add(PlaybackSourceKind.YouTubeIos to {
                    getIosFormatUrl(videoId, audioQualityFormat, connectionMetered)
                })
            }
        }

        InnertubePlayerSource.OldInnertube -> buildList {
            add(PlaybackSourceKind.YouTubeAndroid to {
                getAndroidReelFormatUrl(videoId, audioQualityFormat, connectionMetered)
            })
            if (alternateSourcesEnabled) {
                add(PlaybackSourceKind.YouTubeInnertube to {
                    getInnertubePlayerFormatUrl(videoId, audioQualityFormat, connectionMetered)
                })
                add(PlaybackSourceKind.YouTubeIos to {
                    getIosFormatUrl(videoId, audioQualityFormat, connectionMetered)
                })
            }
        }
    }

    var firstError: Throwable? = null
    attempts.forEach { (sourceKind, resolver) ->
        runCatching { resolver() }
            .onSuccess { uri ->
                PlaybackSourceMonitor.report(sourceKind, videoId, isFallback)
                return uri
            }
            .onFailure { error ->
                if (firstError == null) firstError = error
                Timber.w(
                    error,
                    "Playback source %s failed for %s%s%s",
                    sourceKind.label,
                    videoId,
                    if (isFallback) " fallback" else "",
                    if (alternateSourcesEnabled) ", trying next YouTube source" else ""
                )
            }
    }

    throw (firstError as? Exception ?: UnknownException())
}

private fun <T> pickPreferredFormat(
    high: T?,
    medium: T?,
    low: T?,
    auto: T?,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): T? = when (audioQualityFormat) {
    AudioQualityFormat.High -> high
    AudioQualityFormat.Medium -> medium
    AudioQualityFormat.Low -> low
    AudioQualityFormat.Auto -> if (connectionMetered && isConnectionMeteredEnabled()) medium else auto
}

private suspend fun getInvidiousFormatUrl(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): Uri {
    val response = Invidious.api.videos(videoId)?.getOrThrow()
        ?: throw IllegalStateException("Invidious response unavailable")
    val format = pickPreferredFormat(
        high = response.highestQualityFormat,
        medium = response.mediumQualityFormat,
        low = response.lowestQualityFormat,
        auto = response.autoMaxQualityFormat,
        audioQualityFormat = audioQualityFormat,
        connectionMetered = connectionMetered
    ) ?: throw IllegalStateException("No playable Invidious format found")

    return format.url?.toUri() ?: throw IllegalStateException("Invidious format URL unavailable")
}

private suspend fun getPipedFormatUrl(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): Uri {
    val pipedSession = getPipedSession()
    if (pipedSession.token.isBlank() || pipedSession.apiBaseUrl.toString().isBlank()) {
        throw IllegalStateException("Piped session unavailable")
    }

    val streams = Piped.media.audioStreams(pipedSession.toApiSession(), videoId)?.getOrThrow()
        ?: throw IllegalStateException("Piped audio streams unavailable")
    val sortedStreams = streams
        .filter { !it.videoOnly && it.url.isNotBlank() }
        .sortedBy { it.bitrate }

    val format = pickPreferredFormat(
        high = sortedStreams.lastOrNull(),
        medium = sortedStreams.getOrNull(sortedStreams.lastIndex / 2),
        low = sortedStreams.firstOrNull(),
        auto = sortedStreams.lastOrNull(),
        audioQualityFormat = audioQualityFormat,
        connectionMetered = connectionMetered
    ) ?: throw IllegalStateException("No playable Piped format found")

    return format.url.toUri()
}

private suspend fun resolveAlternateProviderFormatUrl(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean,
    isFallback: Boolean = true
): Uri =
    runCatching {
        getInvidiousFormatUrl(videoId, audioQualityFormat, connectionMetered)
            .also { PlaybackSourceMonitor.report(PlaybackSourceKind.Invidious, videoId, isFallback) }
    }.recoverCatching {
        getPipedFormatUrl(videoId, audioQualityFormat, connectionMetered)
            .also { PlaybackSourceMonitor.report(PlaybackSourceKind.Piped, videoId, isFallback) }
    }.getOrThrow()

private suspend fun findReplacementVideoId(videoId: String): String? {
    val song = Database.songTable.findById(videoId).first() ?: return null
    val title = cleanPrefix(song.title).trim()
    if (title.isBlank()) return null

    val artists = song.artistsText
        ?.split(",")
        ?.map { cleanPrefix(it).trim() }
        ?.filter { it.isNotBlank() }
        .orEmpty()

    val queries = buildList {
        add(title)
        artists.firstOrNull()?.let { add("$title $it") }
        if (artists.size > 1) {
            add((listOf(title) + artists.take(2)).joinToString(" "))
        }
    }.distinct()

    fun normalizeMatchText(value: String): String =
        cleanPrefix(value)
            .lowercase()
            .replace(
                Regex("\\b(official|music video|video|audio|lyrics|visualizer|topic|vevo|hd|4k)\\b"),
                " "
            )
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    fun scoreCandidate(candidateTitle: String, candidateArtist: String): Int {
        val expectedTitle = normalizeMatchText(title)
        val expectedArtists = artists.map(::normalizeMatchText).filter { it.isNotBlank() }
        val normalizedCandidateTitle = normalizeMatchText(candidateTitle)
        val normalizedCandidateArtist = normalizeMatchText(candidateArtist)

        if (normalizedCandidateTitle.isBlank()) return Int.MIN_VALUE
        if (
            normalizedCandidateTitle.contains("mix") ||
            normalizedCandidateTitle.contains("playlist") ||
            normalizedCandidateTitle.contains("full album")
        ) return Int.MIN_VALUE

        var score = 0
        if (normalizedCandidateTitle == expectedTitle) score += 120
        else if (
            normalizedCandidateTitle.contains(expectedTitle) ||
            expectedTitle.contains(normalizedCandidateTitle)
        ) score += 80
        else {
            val expectedTokens = expectedTitle.split(" ").filter { it.length > 1 }.toSet()
            val candidateTokens = normalizedCandidateTitle.split(" ").filter { it.length > 1 }.toSet()
            score += expectedTokens.intersect(candidateTokens).size * 18
        }

        expectedArtists.forEach { artist ->
            if (artist == normalizedCandidateArtist) score += 70
            else if (
                normalizedCandidateArtist.contains(artist) ||
                artist.contains(normalizedCandidateArtist)
            ) score += 45
            else {
                val artistTokens = artist.split(" ").filter { it.length > 1 }.toSet()
                val candidateArtistTokens =
                    normalizedCandidateArtist.split(" ").filter { it.length > 1 }.toSet()
                score += artistTokens.intersect(candidateArtistTokens).size * 14
            }
        }

        return score
    }

    suspend fun searchReplacement(filter: SearchFilter): String? {
        var bestCandidate: Pair<String, Int>? = null

        for (query in queries) {
            val itemsPage = Innertube.searchPage(
                body = SearchBody(query = query, params = filter.value),
                fromMusicShelfRendererContent = { content ->
                    when (filter) {
                        SearchFilter.Song -> Innertube.SongItem.from(content)
                        SearchFilter.Video -> Innertube.VideoItem.from(content)
                        else -> null
                    }
                }
            )?.getOrNull()

            itemsPage?.items?.forEach { item ->
                if (item.key.isBlank() || item.key == videoId) return@forEach

                val candidateTitle = when (item) {
                    is Innertube.SongItem -> item.info?.name.orEmpty()
                    is Innertube.VideoItem -> item.info?.name.orEmpty()
                    else -> ""
                }
                val candidateArtist = when (item) {
                    is Innertube.SongItem ->
                        item.authors?.joinToString(", ") { it.name.orEmpty() }.orEmpty()
                    is Innertube.VideoItem ->
                        item.authors?.joinToString(", ") { it.name.orEmpty() }.orEmpty()
                    else -> ""
                }
                val score = scoreCandidate(candidateTitle, candidateArtist)
                if (score > (bestCandidate?.second ?: Int.MIN_VALUE)) {
                    bestCandidate = item.key to score
                }
            }
        }

        val minimumScore = if (filter == SearchFilter.Song) 80 else 65
        return bestCandidate?.takeIf { it.second >= minimumScore }?.first
    }

    fun searchOmadaReplacement(): String? {
        var bestCandidate: Pair<String, Int>? = null

        queries.forEach { query ->
            runCatching {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val connection =
                    URL("${SecureApiConfig.resolveOmadaSearchApi()}?q=$encodedQuery")
                        .openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val results = JSONArray(response)
                for (index in 0 until results.length()) {
                    val item = results.optJSONObject(index) ?: continue
                    if (item.optString("type") != "video") continue

                    val candidateVideoId = item.optString("videoId").trim()
                    if (candidateVideoId.isBlank() || candidateVideoId == videoId) continue

                    val score = scoreCandidate(
                        candidateTitle = item.optString("title"),
                        candidateArtist = item.optString("author")
                    )
                    if (score > (bestCandidate?.second ?: Int.MIN_VALUE)) {
                        bestCandidate = candidateVideoId to score
                    }
                }
            }
        }

        return bestCandidate?.takeIf { it.second >= 60 }?.first
    }

    return searchReplacement(SearchFilter.Song)
        ?: searchReplacement(SearchFilter.Video)
        ?: searchOmadaReplacement()
}
//</editor-fold>

@UnstableApi
fun DataSpec.process(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): DataSpec {
    return runBlocking(Dispatchers.IO) {
        if (!isNetworkConnected(appContext())) throw NoInternetException()
        if (!videoId.isYouTubeVideoId()) {
            Timber.w("Refusing to resolve non-video playback id=%s", videoId)
            throw PlayableFormatNotFoundException()
        }

        val formatUri = try {
            resolvePrimaryFormatUrl(
                videoId = videoId,
                audioQualityFormat = audioQualityFormat,
                connectionMetered = connectionMetered,
                isFallback = false
            )
        } catch (primaryError: Throwable) {
            Timber.w(primaryError, "All primary YouTube sources failed for %s, trying alternates", videoId)

            if (!isNetworkConnected(appContext())) throw NoInternetException()
            if (!appContext().preferences.getBoolean(alternateSourceRetryKey, true)) {
                throw (primaryError as? Exception ?: UnknownException())
            }

            var alternateUri: Uri? = null

            runCatching {
                resolveAlternateProviderFormatUrl(
                    videoId = videoId,
                    audioQualityFormat = audioQualityFormat,
                    connectionMetered = connectionMetered,
                    isFallback = true
                )
            }.onSuccess { alternateUri = it }
                .onFailure { Timber.w(it, "Alternate providers failed for original videoId=%s", videoId) }

            if (alternateUri == null) {
                val replacementId = runCatching { findReplacementVideoId(videoId) }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() && it != videoId }

                if (replacementId != null) {
                    Timber.i("Found replacement videoId=%s for original=%s", replacementId, videoId)

                    runCatching {
                        resolvePrimaryFormatUrl(
                            videoId = replacementId,
                            audioQualityFormat = audioQualityFormat,
                            connectionMetered = connectionMetered,
                            isFallback = true
                        )
                    }.onSuccess { alternateUri = it }
                        .onFailure {
                            runCatching {
                                resolveAlternateProviderFormatUrl(
                                    videoId = replacementId,
                                    audioQualityFormat = audioQualityFormat,
                                    connectionMetered = connectionMetered,
                                    isFallback = true
                                )
                            }.onSuccess { alternateUri = it }
                                .onFailure { Timber.w(it, "All sources failed for replacement videoId=%s", replacementId) }
                        }
                }
            }

            alternateUri ?: throw (primaryError as? Exception ?: UnknownException())
        }

        withUri(formatUri).subrange(uriPositionOffset)
    }
}

//<editor-fold defaultstate="collapsed" desc="Data source factories">
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
            dataSpec.process(videoId, audioQualityFormat, appContext().isConnectionMetered())
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
//</editor-fold>
