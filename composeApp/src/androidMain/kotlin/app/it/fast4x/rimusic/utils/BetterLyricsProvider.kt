package app.it.fast4x.rimusic.utils

import app.cubic.android.core.network.NetworkClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import org.w3c.dom.Element
import timber.log.Timber
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

data class BetterLyricsResult(
    val syncedLyrics: String?,
    val plainLyrics: String?
)

object BetterLyricsProvider {
    private const val BASE_URL = "https://lyrics-api.boidu.dev/"
    private val endpoints = listOf("getLyrics", "kugou/getLyrics")

    suspend fun lyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int = -1
    ): BetterLyricsResult? = withContext(Dispatchers.IO) {
        if (title.isBlank() || artist.isBlank()) return@withContext null

        endpoints.firstNotNullOfOrNull { endpoint ->
            runCatching {
                val url = (BASE_URL + endpoint).toHttpUrl().newBuilder()
                    .addQueryParameter("s", title.trim())
                    .addQueryParameter("a", artist.trim())
                    .apply {
                        album?.trim()?.takeIf(String::isNotBlank)?.let {
                            addQueryParameter("al", it)
                        }
                        if (durationSeconds > 0) {
                            addQueryParameter("d", durationSeconds.toString())
                        }
                    }
                    .build()

                NetworkClientFactory.getClientWithTimeout(10, 20)
                    .newCall(Request.Builder().url(url).get().build())
                    .execute()
                    .use { response ->
                        if (!response.isSuccessful) return@use null
                        val body = response.body?.string().orEmpty().trim()
                        val ttml = when {
                            body.startsWith("<") -> body
                            body.startsWith("{") -> JSONObject(body)
                                .optString("ttml")
                                .ifBlank { JSONObject(body).optString("lyrics") }
                            else -> body
                        }
                        parseTtml(ttml)
                    }
            }.onFailure {
                Timber.w(it, "BetterLyrics endpoint %s failed", endpoint)
            }.getOrNull()
        }
    }

    private fun parseTtml(ttml: String): BetterLyricsResult? {
        if (ttml.isBlank()) return null
        if (!ttml.trimStart().startsWith("<")) {
            return BetterLyricsResult(syncedLyrics = null, plainLyrics = ttml.trim())
        }

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val document = factory.newDocumentBuilder().parse(ttml.byteInputStream())
        val elements = document.getElementsByTagName("*")
        val lines = buildList {
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                val name = element.localName ?: element.tagName.substringAfter(':')
                if (!name.equals("p", ignoreCase = true)) continue
                val text = element.textContent
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (text.isBlank()) continue
                add(parseTimeSeconds(element.getAttribute("begin")) to text)
            }
        }
        if (lines.isEmpty()) return null

        val plain = lines.joinToString("\n") { it.second }.trim().takeIf(String::isNotBlank)
        val timedLines = lines.filter { it.first != null }
        val synced = timedLines
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n") { (seconds, text) ->
                "${formatLrcTimestamp(seconds ?: 0.0)}$text"
            }
            ?.takeIf(String::isNotBlank)

        return BetterLyricsResult(syncedLyrics = synced, plainLyrics = plain)
    }

    private fun parseTimeSeconds(raw: String): Double? {
        val value = raw.trim()
        if (value.isBlank()) return null
        return when {
            value.endsWith("ms") -> value.removeSuffix("ms").toDoubleOrNull()?.div(1000.0)
            value.endsWith("s") -> value.removeSuffix("s").toDoubleOrNull()
            ":" in value -> {
                val parts = value.split(":")
                when (parts.size) {
                    3 -> parts[0].toDoubleOrNull()?.times(3600)
                        ?.plus(parts[1].toDoubleOrNull()?.times(60) ?: return null)
                        ?.plus(parts[2].toDoubleOrNull() ?: return null)
                    2 -> parts[0].toDoubleOrNull()?.times(60)
                        ?.plus(parts[1].toDoubleOrNull() ?: return null)
                    else -> null
                }
            }
            else -> value.toDoubleOrNull()
        }
    }

    private fun formatLrcTimestamp(seconds: Double): String {
        val totalCentiseconds = (seconds.coerceAtLeast(0.0) * 100).toLong()
        val minutes = totalCentiseconds / 6000
        val remainingSeconds = (totalCentiseconds % 6000) / 100
        val centiseconds = totalCentiseconds % 100
        return String.format(Locale.US, "[%02d:%02d.%02d]", minutes, remainingSeconds, centiseconds)
    }
}
