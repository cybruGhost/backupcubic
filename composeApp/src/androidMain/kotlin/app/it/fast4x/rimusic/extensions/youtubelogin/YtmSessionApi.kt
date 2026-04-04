package app.it.fast4x.rimusic.extensions.youtubelogin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

object YtmSessionApi {

    // Replace with your deployed public cookie-inspection endpoint.
    private const val SESSION_ENDPOINT = "https://ytm-session-hero.lovable.app/api/ytm-session"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchAccountInfo(cookies: String): Result<YtmAccountInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedCookies = YouTubeSessionStore.normalizeCookieString(cookies)
            require(normalizedCookies.isNotBlank()) { "No YouTube Music cookies found yet" }

            val request = Request.Builder()
                .url("$SESSION_ENDPOINT?action=fetch")
                .post(JSONObject().put("cookies", normalizedCookies).toString().toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .build()

            execute(request).use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException(parseError(body, response.code))
                }

                val json = JSONObject(body)
                YtmAccountInfo(
                    hasSession = json.optBoolean("hasSession", false),
                    accountName = json.optString("accountName"),
                    accountEmail = json.optString("accountEmail"),
                    accountChannelHandle = json.optString("accountChannelHandle"),
                    accountThumbnail = json.optString("accountThumbnail")
                )
            }
        }
    }

    suspend fun listAccounts(cookies: String): Result<List<YtmLinkedAccount>> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedCookies = YouTubeSessionStore.normalizeCookieString(cookies)
            require(normalizedCookies.isNotBlank()) { "No YouTube Music cookies found yet" }

            val request = Request.Builder()
                .url("$SESSION_ENDPOINT?action=list_accounts")
                .post(JSONObject().put("cookies", normalizedCookies).toString().toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .build()

            execute(request).use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException(parseError(body, response.code))
                }

                val accounts = JSONObject(body).optJSONArray("accounts") ?: JSONArray()
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

            val body = JSONObject()
                .put("cookies", normalizedCookies)
                .put("authUser", authUser)
                .apply {
                    if (!pageId.isNullOrBlank()) put("pageId", pageId)
                }

            val request = Request.Builder()
                .url("$SESSION_ENDPOINT?action=switch_account")
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

    private fun execute(request: Request) =
        httpClient.newCall(request).execute().also {
            Timber.d("YtmSessionApi %s %s -> %s", request.method, request.url, it.code)
        }

    private fun parseLinkedAccount(json: JSONObject): YtmLinkedAccount =
        YtmLinkedAccount(
            accountName = json.optString("accountName"),
            accountEmail = json.optString("accountEmail"),
            channelHandle = json.optString("channelHandle"),
            accountThumbnail = json.optString("accountThumbnail"),
            subscribers = json.optString("subscribers"),
            isSelected = json.optBoolean("isSelected"),
            authUser = json.optString("authUser"),
            pageId = json.optString("pageId")
        )

    private fun parseApiSession(json: JSONObject, fallbackCookie: String): YtmApiSession =
        YtmApiSession(
            hasSession = json.optBoolean("hasSession", true),
            cookie = json.optString("cookies").ifBlank {
                json.optString("cookie").ifBlank { fallbackCookie }
            },
            visitorData = json.optString("visitorData"),
            dataSyncId = json.optString("dataSyncId"),
            accountName = json.optString("accountName"),
            accountEmail = json.optString("accountEmail"),
            accountChannelHandle = json.optString("accountChannelHandle"),
            accountThumbnail = json.optString("accountThumbnail")
        )

    private fun parseError(body: String, statusCode: Int): String =
        runCatching {
            JSONObject(body).optString("error").ifBlank {
                JSONObject(body).optString("message").ifBlank {
                    "Request failed with status $statusCode"
                }
            }
        }.getOrDefault("Request failed with status $statusCode")
}
