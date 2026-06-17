package app.it.fast4x.rimusic.ui.screens.album

import app.it.fast4x.rimusic.extensions.youtubelogin.YtmAlbumTrack
import app.it.fast4x.rimusic.models.Song
import it.fast4x.innertube.Innertube
import java.util.Locale

private val albumIdentityWhitespace = Regex("\\s+")

private fun String?.albumIdentityPart(): String =
    this.orEmpty()
        .trim()
        .lowercase(Locale.ROOT)
        .replace(albumIdentityWhitespace, " ")

private inline fun <T> List<T>.deduplicateAdjacentBy(
    identity: (T) -> String
): List<T> {
    var previousIdentity: String? = null
    return filter { item ->
        val currentIdentity = identity(item)
        val duplicate = currentIdentity.isNotBlank() && currentIdentity == previousIdentity
        previousIdentity = currentIdentity
        !duplicate
    }
}

internal fun List<YtmAlbumTrack>.deduplicateAlbumTracks(
    fallbackArtist: String
): List<YtmAlbumTrack> = deduplicateAdjacentBy { track ->
    listOf(
        track.title.albumIdentityPart(),
        track.artistsText.ifBlank { fallbackArtist }.albumIdentityPart(),
        track.duration.albumIdentityPart()
    ).joinToString("|")
}

internal fun List<Innertube.SongItem>.deduplicateAlbumItems(): List<Innertube.SongItem> =
    deduplicateAdjacentBy { item ->
        listOf(
            item.title.albumIdentityPart(),
            item.authors.orEmpty().joinToString(",") { it.name.orEmpty() }.albumIdentityPart(),
            item.durationText.albumIdentityPart()
        ).joinToString("|")
    }

internal fun List<Song>.deduplicateAlbumSongs(): List<Song> =
    deduplicateAdjacentBy { song ->
        listOf(
            song.title.albumIdentityPart(),
            song.artistsText.albumIdentityPart(),
            song.durationText.albumIdentityPart()
        ).joinToString("|")
    }
