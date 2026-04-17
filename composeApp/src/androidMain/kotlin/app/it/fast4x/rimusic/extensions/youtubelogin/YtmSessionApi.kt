package app.it.fast4x.rimusic.extensions.youtubelogin

import app.cubic.android.core.network.NetworkQualityHelper
import app.cubic.android.core.network.enum.NetworkQuality
import app.it.fast4x.rimusic.appContext
import app.it.fast4x.rimusic.appRunningInBackground
import app.it.fast4x.rimusic.appVisibilityInBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.util.Collections
import java.util.concurrent.TimeUnit

object YtmSessionApi {
    private const val SESSION_ENDPOINT = "https://ytm-session-hero.lovable.app/api/ytm-session"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val activeCalls = Collections.synchronizedSet(mutableSetOf<Call>())
    private val visibilityScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        visibilityScope.launch {
            appVisibilityInBackground.collect { isInBackground ->
                if (isInBackground) {
                    cancelActiveCalls()
                }
            }
        }
    }

    suspend fun fetchAccountInfo(cookies: String): Result<YtmAccountInfo> = withContext(Dispatchers.IO) {
        post("fetch", cookies) { json ->
            YtmAccountInfo(
                hasSession = json.optBoolean("hasSession", false),
                accountName = json.optString("accountName"),
                accountEmail = json.optString("accountEmail"),
                accountChannelHandle = json.optString("accountChannelHandle"),
                accountThumbnail = json.optString("accountThumbnail")
            )
        }
    }

    suspend fun listAccounts(cookies: String): Result<List<YtmLinkedAccount>> = withContext(Dispatchers.IO) {
        post("list_accounts", cookies) { json ->
            val accounts = json.optJSONArray("accounts") ?: JSONArray()
            List(accounts.length()) { index ->
                parseLinkedAccount(accounts.optJSONObject(index) ?: JSONObject())
            }.distinctBy { account ->
                listOf(
                    account.accountEmail.trim().lowercase(),
                    account.channelHandle.trim().lowercase(),
                    account.pageId.trim(),
                    account.accountName.trim().lowercase()
                ).joinToString("|")
            }
        }
    }

    suspend fun switchAccount(
        cookies: String,
        authUser: String,
        pageId: String?
    ): Result<YtmApiSession> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedCookies = YouTubeSessionStore.normalizeCookieString(cookies)
            require(normalizedCookies.isNotBlank()) { "No YouTube Music cookies found yet" }
            require(authUser.isNotBlank()) { "Missing authUser for selected account" }

            val body = JSONObject().put("cookies", normalizedCookies)

            val url = buildString {
                append(SESSION_ENDPOINT)
                append("?action=switch_account&authuser=").append(authUser)
                if (!pageId.isNullOrBlank()) append("&pageid=").append(pageId)
            }

            val request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .build()

            execute(request).use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException(parseError(responseBody, response.code))
                }

                parseApiSession(JSONObject(responseBody), fallbackCookie = normalizedCookies)
            }
        }
    }

    suspend fun fetchPlaylists(
        cookies: String,
        authUser: String? = null,
        pageId: String? = null
    ): Result<List<YtmPlaylist>> = postLibrary("playlists", cookies, authUser, pageId) { json ->
        val arr = json.optJSONArray("playlists") ?: JSONArray()
        List(arr.length()) { index ->
            val item = arr.optJSONObject(index) ?: JSONObject()
            val subtitle = item.optString("subtitle")
            YtmPlaylist(
                playlistId = item.optString("playlistId").normalizedField(),
                browseId = item.optString("browseId").normalizedField(),
                rawPlaylistId = item.optString("rawPlaylistId").normalizedField(),
                title = item.optString("title").normalizedField(),
                name = item.optString("name").normalizedField(),
                thumbnail = item.optString("thumbnail").normalizedField(),
                thumbnailUrl = item.optString("thumbnailUrl").normalizedField(),
                songCount = item.optString("songCount").normalizedField().ifBlank { extractSongCount(subtitle) },
                subtitle = subtitle.normalizedField()
            )
        }
    }

    suspend fun fetchHomeFeed(
        cookies: String,
        authUser: String? = null,
        pageId: String? = null
    ): Result<List<YtmHomeSection>> = postLibrary("home", cookies, authUser, pageId) { json ->
        val arr = json.optJSONArray("sections") ?: JSONArray()
        List(arr.length()) { index ->
            val section = arr.optJSONObject(index) ?: JSONObject()
            val items = section.optJSONArray("items") ?: JSONArray()
            YtmHomeSection(
                title = section.optString("title").normalizedField(),
                subtitle = section.optString("subtitle").normalizedField(),
                browseId = section.optString("browseId").normalizedField(),
                params = section.optString("params").normalizedField(),
                type = section.optString("type").normalizedField(),
                itemCount = section.optInt("itemCount"),
                hasMore = section.optBoolean("hasMore"),
                items = List(items.length()) { itemIndex ->
                    val item = items.optJSONObject(itemIndex) ?: JSONObject()
                    YtmHomeSectionItem(
                        id = item.optString("id").normalizedField(),
                        videoId = item.optString("videoId").normalizedField(),
                        playlistId = item.optString("playlistId").normalizedField(),
                        browseId = item.optString("browseId").normalizedField(),
                        title = item.optString("title").normalizedField(),
                        subtitle = item.optString("subtitle").normalizedField(),
                        artistsText = item.optString("artistsText").normalizedField(),
                        artistId = item.optString("artistId").normalizedField(),
                        artistIds = item.optJSONArray("artistIds")?.let { artistIds ->
                            List(artistIds.length()) { artistIndex -> artistIds.optString(artistIndex).normalizedField() }
                                .filter { it.isNotBlank() }
                        } ?: emptyList(),
                        album = item.optString("album").normalizedField(),
                        albumId = item.optString("albumId").normalizedField(),
                        thumbnail = item.optString("thumbnail").normalizedField(),
                        thumbnailUrl = item.optString("thumbnailUrl").normalizedField(),
                        type = item.optString("type").normalizedField()
                    )
                }
            )
        }
    }

    suspend fun fetchPlaylistSongs(
        cookies: String,
        playlistId: String,
        authUser: String? = null,
        pageId: String? = null
    ): Result<List<YtmSong>> = withContext(Dispatchers.IO) {
        require(playlistId.isNotBlank()) { "Missing playlistId" }
        val url = buildString {
            append(SESSION_ENDPOINT)
            append("?action=playlist_songs")
            append("&playlistId=").append(playlistId)
            if (!authUser.isNullOrBlank()) append("&authuser=").append(authUser)
            if (!pageId.isNullOrBlank()) append("&pageid=").append(pageId)
        }
        postUrl(url, cookies, ::parseSongs)
    }

    suspend fun fetchLikedSongs(
        cookies: String,
        authUser: String? = null,
        pageId: String? = null
    ): Result<List<YtmSong>> = postLibrary("liked_songs", cookies, authUser, pageId, ::parseSongs)

    suspend fun fetchHistory(
        cookies: String,
        authUser: String? = null,
        pageId: String? = null
    ): Result<List<YtmSong>> = postLibrary("history", cookies, authUser, pageId, ::parseSongs)

    suspend fun fetchArtists(
        cookies: String,
        authUser: String? = null,
        pageId: String? = null
    ): Result<List<YtmArtist>> = postLibrary("artists", cookies, authUser, pageId) { json ->
        val arr = json.optJSONArray("artists") ?: JSONArray()
        List(arr.length()) { index ->
            val item = arr.optJSONObject(index) ?: JSONObject()
            YtmArtist(
                browseId = item.optString("browseId").normalizedField(),
                name = item.optString("name").normalizedField(),
                thumbnail = item.optString("thumbnail").normalizedField(),
                subscribers = item.optString("subscribers").normalizedField()
            )
        }
    }

    suspend fun fetchAlbums(
        cookies: String,
        authUser: String? = null,
        pageId: String? = null
    ): Result<List<YtmAlbum>> = postLibrary("albums", cookies, authUser, pageId) { json ->
        val arr = json.optJSONArray("albums") ?: JSONArray()
        List(arr.length()) { index ->
            val item = arr.optJSONObject(index) ?: JSONObject()
            YtmAlbum(
                browseId = item.optString("browseId").normalizedField(),
                playlistId = item.optString("playlistId").normalizedField(),
                title = item.optString("title").normalizedField(),
                artist = item.optString("artist").normalizedField(),
                thumbnail = item.optString("thumbnail").normalizedField(),
                year = item.optString("year").normalizedField(),
                type = item.optString("type").normalizedField()
            )
        }
    }

    private fun execute(request: Request): Response {
        ensureForeground("YtmSessionApi")
        val call = httpClient.newCall(request)
        activeCalls += call
        return try {
            call.execute().also {
                Timber.d("YtmSessionApi %s %s -> %s", request.method, request.url, it.code)
            }
        } catch (e: IOException) {
            throw IOException(describeConnectivityFailure(e.message), e)
        } finally {
            activeCalls -= call
        }
    }

    private fun parseLinkedAccount(json: JSONObject): YtmLinkedAccount =
        YtmLinkedAccount(
            accountName = json.optString("accountName").normalizedField(),
            accountEmail = json.optString("accountEmail").normalizedField(),
            channelHandle = json.optString("channelHandle").normalizedField(),
            accountThumbnail = json.optString("accountThumbnail").normalizedField(),
            subscribers = json.optString("subscribers").normalizedField(),
            isSelected = json.optBoolean("isSelected"),
            authUser = json.optString("authUser").normalizedField(),
            pageId = json.optString("pageId").normalizedField()
        )

    private fun parseApiSession(json: JSONObject, fallbackCookie: String): YtmApiSession =
        YtmApiSession(
            hasSession = json.optBoolean("hasSession", true),
            cookie = json.optString("cookies").normalizedField().ifBlank {
                json.optString("cookie").normalizedField().ifBlank { fallbackCookie }
            },
            visitorData = json.optString("visitorData").normalizedField(),
            dataSyncId = json.optString("dataSyncId").normalizedField(),
            authUser = json.optString("authUser").normalizedField(),
            pageId = json.optString("pageId").normalizedField(),
            accountName = json.optString("accountName").normalizedField(),
            accountEmail = json.optString("accountEmail").normalizedField(),
            accountChannelHandle = json.optString("accountChannelHandle").normalizedField(),
            accountThumbnail = json.optString("accountThumbnail").normalizedField()
        )

    private fun parseSongs(json: JSONObject): List<YtmSong> {
        val songs = json.optJSONArray("songs") ?: JSONArray()
        return List(songs.length()) { index ->
            val item = songs.optJSONObject(index) ?: JSONObject()
            YtmSong(
                id = item.optString("id").normalizedField().ifBlank { item.optString("videoId").normalizedField() },
                videoId = item.optString("videoId").normalizedField(),
                title = item.optString("title").normalizedField(),
                artist = item.optString("artist").normalizedField(),
                artistsText = item.optString("artistsText").normalizedField(),
                artistId = item.optString("artistId").normalizedField(),
                artistIds = item.optJSONArray("artistIds")?.let { artistIds ->
                    List(artistIds.length()) { artistIndex -> artistIds.optString(artistIndex).normalizedField() }
                        .filter { it.isNotBlank() }
                } ?: emptyList(),
                artists = item.optJSONArray("artists")?.let { artists ->
                    List(artists.length()) { artistIndex ->
                        val artist = artists.optJSONObject(artistIndex) ?: JSONObject()
                        YtmArtistRef(
                            id = artist.optString("id").normalizedField(),
                            name = artist.optString("name").normalizedField()
                        )
                    }
                        .filter { it.id.isNotBlank() || it.name.isNotBlank() }
                } ?: emptyList(),
                album = item.optString("album").normalizedField(),
                albumId = item.optString("albumId").normalizedField(),
                thumbnail = item.optString("thumbnail").normalizedField(),
                thumbnailUrl = item.optString("thumbnailUrl").normalizedField(),
                duration = item.optString("duration").normalizedField(),
                durationText = item.optString("durationText").normalizedField(),
                setVideoId = item.optString("setVideoId").normalizedField(),
                position = item.optInt("position", -1),
                dateAdded = item.optString("dateAdded").normalizedField(),
                isAvailable = item.optBoolean("isAvailable", true)
            )
        }
    }

    private fun extractSongCount(subtitle: String): String =
        Regex("(\\d+)\\s+(?:tracks?|songs?)", RegexOption.IGNORE_CASE)
            .find(subtitle)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()

    private suspend fun <T> postLibrary(
        action: String,
        cookies: String,
        authUser: String?,
        pageId: String?,
        parse: (JSONObject) -> T
    ): Result<T> {
        val url = buildString {
            append(SESSION_ENDPOINT)
            append("?action=").append(action)
            if (!authUser.isNullOrBlank()) append("&authuser=").append(authUser)
            if (!pageId.isNullOrBlank()) append("&pageid=").append(pageId)
        }
        return postUrl(url, cookies, parse)
    }

    private suspend fun <T> post(
        action: String,
        cookies: String,
        parse: (JSONObject) -> T
    ): Result<T> = postUrl("$SESSION_ENDPOINT?action=$action", cookies, parse)

    private suspend fun <T> postUrl(
        url: String,
        cookies: String,
        parse: (JSONObject) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedCookies = YouTubeSessionStore.normalizeCookieString(cookies)
            require(normalizedCookies.isNotBlank()) { "No YouTube Music cookies found yet" }

            val request = Request.Builder()
                .url(url)
                .post(JSONObject().put("cookies", normalizedCookies).toString().toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .build()

            execute(request).use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException(parseError(body, response.code))
                }
                parse(JSONObject(body))
            }
        }
    }

    private fun ensureForeground(owner: String) {
        if (appRunningInBackground) {
            throw CancellationException("$owner skipped while app is in background")
        }
    }

    private fun cancelActiveCalls() {
        val calls = synchronized(activeCalls) { activeCalls.toList() }
        calls.forEach { call ->
            runCatching { call.cancel() }
                .onFailure { Timber.w(it, "YtmSessionApi failed cancelling background call") }
        }
        activeCalls.clear()
    }

    private fun parseError(body: String, statusCode: Int): String =
        runCatching {
            JSONObject(body).optString("error").ifBlank {
                JSONObject(body).optString("message").ifBlank {
                    describeHttpFailure(statusCode)
                }
            }
        }.getOrDefault(describeHttpFailure(statusCode))

    private fun describeConnectivityFailure(originalMessage: String?): String {
        val context = appContext()
        val isAvailable = NetworkQualityHelper.isNetworkAvailable(context)
        val isConnected = NetworkQualityHelper.isNetworkConnected(context)
        val quality = NetworkQualityHelper.getCurrentNetworkQuality(context)

        return when {
            !isAvailable -> "No internet connection available."
            !isConnected -> "Internet connection is present but not validated yet."
            quality == NetworkQuality.LOW -> "Connection is weak. Loading may buffer or fail temporarily."
            !originalMessage.isNullOrBlank() -> originalMessage
            else -> "Network request failed."
        }
    }

    private fun describeHttpFailure(statusCode: Int): String =
        when (statusCode) {
            401, 403 -> "Session expired or account access was denied."
            404 -> "Requested YouTube Music resource was not found."
            in 500..599 -> "YouTube Music session service is having trouble right now."
            else -> "Request failed with status $statusCode"
        }

    private fun String.normalizedField(): String =
        trim().takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()
}
