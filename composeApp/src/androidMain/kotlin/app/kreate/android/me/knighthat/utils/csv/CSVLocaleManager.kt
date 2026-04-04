package app.kreate.android.me.knighthat.utils.csv

import android.content.Context
import androidx.compose.runtime.Composable
import app.it.fast4x.rimusic.appContext

object CSVLocaleManager {

    private val canonicalHeaders = linkedMapOf(
        "PlaylistBrowseId" to setOf(
            "playlistbrowseid", "playlist browse id", "playlist id", "id playlist", "id de playlist",
            "id de la playlist", "id playlista", "id playlisti", "playlist-id", "playlist_id"
        ),
        "PlaylistName" to setOf(
            "playlistname", "playlist name", "name playlist", "nome playlist", "nombre playlist",
            "naam playlist", "nome da playlist", "namn spellista", "nama playlist",
            "プレイリスト名", "اسم قائمة التشغيل", "çalma listesi adı"
        ),
        "MediaId" to setOf(
            "mediaid", "media id", "videoid", "video id", "youtube id", "yt id", "track id",
            "id media", "id video", "id de video", "id de mídia", "id video youtube", "medya kimligi"
        ),
        "Title" to setOf(
            "title", "track name", "song name", "track title", "song title", "titel", "titolo",
            "titulo", "título", "titre", "naam nummer", "tracknaam", "sangtitel", "judul",
            "başlık", "cancion", "canción", "曲名", "العنوان"
        ),
        "Artists" to setOf(
            "artists", "artist", "artist name", "artist name(s)", "artist names", "song artist",
            "artists name", "künstler", "interprete", "artista", "artistas", "artiste",
            "artiest", "artiesten", "konstnär", "pengarang", "sanatçı", "アーティスト", "الفنان"
        ),
        "Duration" to setOf(
            "duration", "track duration", "track duration (ms)", "duration ms", "duration (ms)",
            "length", "runtime", "duracion", "duración", "durée", "dauer", "durata", "duração",
            "speltid", "süre", "durasi", "再生時間", "المدة"
        ),
        "ThumbnailUrl" to setOf(
            "thumbnailurl", "thumbnail url", "image url", "cover url", "album image url",
            "artwork url", "thumbnail", "cover", "miniatura", "miniatur", "capa", "afbeelding url",
            "bild url", "resim url", "gambar", "サムネイル", "رابط الصورة"
        ),
        "AlbumId" to setOf(
            "albumid", "album id", "id album", "id de album", "id de l'album", "álbum id", "album-id"
        ),
        "AlbumTitle" to setOf(
            "albumtitle", "album title", "album name", "album", "álbum", "albumnaam",
            "albumtitel", "nome do álbum", "titulo del album", "titre de l'album", "アルバム", "الألبوم"
        ),
        "ArtistIds" to setOf(
            "artistids", "artist ids", "artists ids", "artist id list", "ids artista", "ids artistas"
        ),
        "Track URI" to setOf(
            "track uri", "spotify track uri", "uri", "trackuri"
        ),
        "Track Name" to setOf(
            "track name", "song name", "track title", "title", "titel", "titolo", "titulo",
            "título", "titre", "tracknaam", "judul", "başlık", "曲名", "العنوان"
        ),
        "Artist Name(s)" to setOf(
            "artist name(s)", "artist names", "artist name", "artists", "artist", "künstler",
            "interprete", "artista", "artistas", "artiste", "artiest", "artiesten", "pengarang",
            "sanatçı", "アーティスト", "الفنان"
        ),
        "Explicit" to setOf(
            "explicit", "explicit track", "explicit content", "eksplizit", "esplicito", "explícito",
            "explicite", "expliciet", "açık", "eksplisit"
        )
    )

    private val localeHints = mapOf(
        "de" to setOf("künstler", "dauer", "titel", "wiedergabeliste"),
        "en" to setOf("track name", "artist name(s)", "playlist name"),
        "es" to setOf("duración", "artistas", "lista de reproducción", "título"),
        "fr" to setOf("durée", "artiste", "titre", "playlist"),
        "it" to setOf("durata", "artista", "titolo", "playlist"),
        "nl" to setOf("afspeellijst", "duur", "artiest", "titel"),
        "pt" to setOf("duração", "artista", "playlist", "título"),
        "sv" to setOf("spellista", "speltid", "artist", "titel"),
        "ar" to setOf("قائمة التشغيل", "المدة", "الفنان", "العنوان"),
        "ja" to setOf("プレイリスト", "アーティスト", "曲名", "再生時間"),
        "tr" to setOf("çalma listesi", "sanatçı", "başlık", "süre"),
        "id" to setOf("playlist", "durasi", "artis", "judul")
    )

    private var initialized = false

    fun initialize(context: Context = appContext()) {
        initialized = true
    }

    fun normalizeRow(row: Map<String, String>): Map<String, String> {
        if (!initialized) initialize()

        val normalized = row.toMutableMap()
        val lookup = row.entries.associateBy { normalizeHeader(it.key) }

        canonicalHeaders.forEach { (canonical, aliases) ->
            val matchedEntry = lookup[normalizeHeader(canonical)]
                ?: aliases.firstNotNullOfOrNull { alias -> lookup[normalizeHeader(alias)] }

            if (matchedEntry != null) {
                normalized[canonical] = matchedEntry.value
            }
        }

        return normalized
    }

    fun detectLocale(headers: Set<String>): String? {
        if (!initialized) initialize()

        val normalizedHeaders = headers.map(::normalizeHeader)
        val bestMatch = localeHints.entries.maxByOrNull { (_, hints) ->
            hints.count { hint ->
                val normalizedHint = normalizeHeader(hint)
                normalizedHeaders.any { header ->
                    header == normalizedHint || header.contains(normalizedHint)
                }
            }
        }

        return bestMatch?.key
    }

    fun isSpotifyFormat(headers: Set<String>): Boolean {
        val normalized = normalizeRow(headers.associateWith { "" }).keys
        return normalized.contains("Track Name") && normalized.contains("Artist Name(s)")
    }

    fun isCustomFormat(headers: Set<String>): Boolean {
        val normalized = normalizeRow(headers.associateWith { "" }).keys
        return normalized.containsAll(
            setOf("PlaylistBrowseId", "PlaylistName", "MediaId", "Title", "Artists", "Duration")
        )
    }

    fun getSupportedLocales(): List<String> = localeHints.keys.toList()

    @Composable
    fun getLocaleDisplayName(locale: String): String = getSimpleLocaleName(locale)

    fun getSimpleLocaleName(locale: String): String = when (locale) {
        "de" -> "German"
        "en" -> "English"
        "es" -> "Spanish"
        "fr" -> "French"
        "it" -> "Italian"
        "nl" -> "Dutch"
        "pt" -> "Portuguese"
        "sv" -> "Swedish"
        "ar" -> "Arabic"
        "ja" -> "Japanese"
        "tr" -> "Turkish"
        "id" -> "Indonesian"
        else -> locale
    }

    private fun normalizeHeader(value: String): String =
        value.lowercase()
            .replace("&", "and")
            .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
}
