package app.it.fast4x.rimusic.recognition

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class RecognizedTrack(
    val title: String,
    val subtitle: String,
    val coverUrl: String? = null,
    val backgroundUrl: String? = null,
    val genre: String? = null
)

class ShazamRepository {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val signature by lazy { ShazamSignature() }

    suspend fun identify(duration: Int, data: ByteArray): RecognizedTrack? {
        val timestamp = Calendar.getInstance().timeInMillis.toInt()
        val requestName = Random(timestamp).nextLong().toString()
        val signatureValue = runCatching {
            signature.safeCreate(data.toShortArray())
        }.getOrNull() ?: return null

        val requestBody = JSONObject().apply {
            put(
                "geolocation",
                JSONObject().apply {
                    put("altitude", Random(timestamp).nextDouble() * 400 + 100)
                    put("latitude", Random(timestamp).nextDouble() * 180 - 90)
                    put("longitude", Random(timestamp).nextDouble() * 360 - 180)
                }
            )
            put(
                "signature",
                JSONObject().apply {
                    put("samplems", duration * 1000)
                    put("timestamp", timestamp)
                    put("uri", signatureValue)
                }
            )
            put("timestamp", timestamp)
            put("timezone", TIMEZONES.random())
        }.toString()

        val url =
            "${BASE_URL}discovery/v5/en/US/android/-/tag/${uuidFromNamespace(NAMESPACE_DNS, requestName)}/${uuidFromNamespace(NAMESPACE_URL, requestName)}" +
                "?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&video=v3"

        val response = client.newCall(
            Request.Builder()
                .url(url)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", USER_AGENTS.random())
                .header("Content-Language", "en_US")
                .header("Content-Type", "application/json")
                .build()
        ).execute()

        if (!response.isSuccessful) return null

        val body = response.body?.use { it.string() } ?: return null
        return parseTrack(body)
    }

    private fun parseTrack(json: String): RecognizedTrack? {
        val root = JSONObject(json)
        val track = root.optJSONObject("track") ?: return null
        val title = track.optString("title").trim()
        val subtitle = track.optString("subtitle").trim()
        if (title.isBlank() || subtitle.isBlank()) return null

        val images = track.optJSONObject("images")
        val genres = track.optJSONObject("genres")
        return RecognizedTrack(
            title = title,
            subtitle = subtitle,
            coverUrl = images?.optString("coverarthq").takeUnless { it.isNullOrBlank() }
                ?: images?.optString("coverart").takeUnless { it.isNullOrBlank() },
            backgroundUrl = images?.optString("background").takeUnless { it.isNullOrBlank() },
            genre = genres?.optString("primary").takeUnless { it.isNullOrBlank() }
        )
    }

    private fun ByteArray.toShortArray(): ShortArray {
        val shorts = ShortArray(size / 2)
        ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return shorts
    }

    private fun uuidFromNamespace(namespace: UUID, value: String): String {
        val namespaceBytes = ByteBuffer.allocate(16)
            .putLong(namespace.mostSignificantBits)
            .putLong(namespace.leastSignificantBits)
            .array()
        val digest = MessageDigest.getInstance("SHA-1").apply {
            update(namespaceBytes)
            update(value.toByteArray())
        }.digest().copyOf(16)

        digest[6] = ((digest[6].toInt() and 0x0F) or 0x50).toByte()
        digest[8] = ((digest[8].toInt() and 0x3F) or 0x80).toByte()

        val buffer = ByteBuffer.wrap(digest)
        return UUID(buffer.long, buffer.long).toString()
    }

    companion object {
        private const val BASE_URL = "https://amp.shazam.com/"
        private val NAMESPACE_DNS = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
        private val NAMESPACE_URL = UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8")
        private val USER_AGENTS = listOf(
            "Dalvik/2.1.0 (Linux; U; Android 6.0.1; SM-G920F Build/MMB29K)",
            "Dalvik/2.1.0 (Linux; U; Android 5.0.2; VS980 4G Build/LRX22G)"
        )
        private val TIMEZONES = listOf(
            "Europe/London",
            "America/New_York",
            "Asia/Tokyo",
            Locale.getDefault().toLanguageTag().ifBlank { "Africa/Nairobi" }
        )
    }
}
