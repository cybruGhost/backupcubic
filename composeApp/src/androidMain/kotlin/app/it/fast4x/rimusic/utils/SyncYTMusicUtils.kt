package app.it.fast4x.rimusic.utils

import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.kreate.android.R
import app.it.fast4x.rimusic.extensions.youtubelogin.YouTubeRequestThrottler
import app.it.fast4x.rimusic.extensions.youtubelogin.YouTubeSessionStore
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.utils.completed
import app.it.fast4x.rimusic.Database
import app.it.fast4x.rimusic.isAutoSyncEnabled
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.DynamicColor
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import app.kreate.android.me.knighthat.utils.Toaster
import app.it.fast4x.rimusic.cleanPrefix
import timber.log.Timber

private val ytmAlbumThumbnailCache = mutableMapOf<String, String?>()

private suspend fun Innertube.SongItem.asNormalizedSong(): app.it.fast4x.rimusic.models.Song {
    val cleanedFallbackThumbnail = asSong.thumbnailUrl?.trim()?.takeIf { it.isNotBlank() }
    val albumId = album?.endpoint?.browseId?.trim().orEmpty()

    val albumThumbnail = if (albumId.isBlank()) {
        null
    } else {
        val resolvedThumbnail = ytmAlbumThumbnailCache[albumId]
            ?: Database.albumTable.findById(albumId).first()?.thumbnailUrl?.trim()?.takeIf { it.isNotBlank() }
            ?: safeYtmCall(
                label = "ytmAlbumThumbnail:$albumId",
                timeoutMs = 12_000L,
                maxRetries = 1
            ) {
                YtMusic.getAlbum(albumId, withSongs = false)
            }?.album?.thumbnail?.url?.trim()?.takeIf { it.isNotBlank() }?.also { thumbnail ->
                Database.albumTable.upsert(
                    Album(
                        id = albumId,
                        title = album?.name,
                        thumbnailUrl = thumbnail,
                        isYoutubeAlbum = true
                    )
                )
            }

        ytmAlbumThumbnailCache[albumId] = resolvedThumbnail
        resolvedThumbnail
    }

    return asSong.copy(
        id = key.trim(),
        title = cleanPrefix(asSong.title).trim(),
        artistsText = asSong.artistsText?.trim(),
        thumbnailUrl = albumThumbnail ?: cleanedFallbackThumbnail
    )
}

private suspend fun <T> safeYtmCall(
    label: String,
    timeoutMs: Long = 15_000L,
    maxRetries: Int = 2,
    block: suspend () -> Result<T>
): T? {
    repeat(maxRetries) { attempt ->
        try {
            return withTimeout(timeoutMs) {
                block().getOrNull()
            }
        } catch (error: Exception) {
            Timber.w(error, "YTM sync call failed label=%s attempt=%s/%s", label, attempt + 1, maxRetries)
            if (attempt == maxRetries - 1) return null
            delay(750L * (attempt + 1))
        }
    }
    return null
}

@OptIn(UnstableApi::class)
fun ytmPrivatePlaylistSync(playlist: Playlist, playlistId: Long) {
    if (!playlist.isYoutubePlaylist || playlist.browseId.isNullOrBlank()) {
        println("ytmPrivatePlaylistSync skipped: playlist ${playlist.name} is not a YT playlist")
        return
    }

    CoroutineScope(Dispatchers.IO).launch {
        runCatching {
            val browseId = playlist.browseId ?: return@launch
            val remotePlaylist = safeYtmCall(
                label = "ytmPrivatePlaylistSync:$browseId",
                timeoutMs = 30_000L
            ) {
                YtMusic.getPlaylist(playlistId = browseId).completed()
            } ?: return@launch

            val normalizedSongs = remotePlaylist.songs
                .map { it.asNormalizedSong() }
                .filter { song -> song.id.isNotBlank() && song.title.isNotBlank() }
                .distinctBy { song -> song.id }

            Database.asyncTransaction {
                val currentPlaylist = playlist.copy(
                    id = playlistId,
                    isEditable = remotePlaylist.isEditable == true || playlist.isEditable
                )

                playlistTable.insertIgnore(currentPlaylist)
                playlistTable.update(currentPlaylist)
                normalizedSongs.forEach(songTable::insertIgnore)

                if (normalizedSongs.isNotEmpty()) {
                    songPlaylistMapTable.clear(currentPlaylist.id)
                    songPlaylistMapTable.updateReplace(
                        normalizedSongs.mapIndexed { index, song ->
                            app.it.fast4x.rimusic.models.SongPlaylistMap(
                                songId = song.id,
                                playlistId = currentPlaylist.id,
                                position = index
                            )
                        }
                    )
                }
            }
        }.onFailure { error ->
            Timber.e(error, "ytmPrivatePlaylistSync failed playlistId=%s browseId=%s", playlistId, playlist.browseId)
        }
    }
}

suspend fun importYTMSubscribedChannels(): Boolean {
    println("importYTMSubscribedChannels isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()} and isAutoSyncEnabled() = ${isAutoSyncEnabled()}")
    if (isYouTubeSyncEnabled()) {
        YouTubeSessionStore.applyCurrentSession()

        Toaster.n( R.string.syncing, Toast.LENGTH_LONG )

        val page = safeYtmCall("importYTMSubscribedChannels") {
            YouTubeRequestThrottler.run {
                Innertube.library("FEmusic_library_corpus_artists").completed()
            }
        } ?: return false

        runCatching {
            val ytmArtists = page.items.filterIsInstance<Innertube.ArtistItem>()

            println("YTM artists: $ytmArtists")

            ytmArtists.forEach { remoteArtist ->
                if (remoteArtist.key.isBlank() || remoteArtist.title.isNullOrBlank()) return@forEach
                var localArtist = Database.artistTable.findById( remoteArtist.key ).first()
                println("Local artist: $localArtist")
                println("Remote artist: $remoteArtist")

                if (localArtist == null) {
                    localArtist = Artist(
                        id = remoteArtist.key,
                        name = remoteArtist.title,
                        thumbnailUrl = remoteArtist.thumbnail?.url,
                        bookmarkedAt = System.currentTimeMillis(),
                        isYoutubeArtist = true
                    )
                    Database.artistTable.upsert( localArtist )
                } else {
                    localArtist.copy(
                        bookmarkedAt = localArtist.bookmarkedAt ?: System.currentTimeMillis(),
                        thumbnailUrl = remoteArtist.thumbnail?.url,
                        isYoutubeArtist = true
                    ).let( Database.artistTable::update )
                }
            }

            Database.artistTable
                    .allFollowing()
                    .first()
                    .filter { artist ->
                        artist.isYoutubeArtist && artist.id !in ytmArtists.map { it.key }
                    }
                    .map { it.copy( isYoutubeArtist = false, bookmarkedAt = null ) }
                    .forEach( Database.artistTable::update )
        }.onFailure {
            println("Error importing YTM subscribed artists channels: ${it.stackTraceToString()}")
            Timber.e(it, "importYTMSubscribedChannels failed")
            return false
        }
        return true
    } else
        return false
}

suspend fun importYTMLikedAlbums(): Boolean {
    println("importYTMLikedAlbums isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()} and isAutoSyncEnabled() = ${isAutoSyncEnabled()}")
    if (isYouTubeSyncEnabled()) {
        YouTubeSessionStore.applyCurrentSession()

        Toaster.n( R.string.syncing, Toast.LENGTH_LONG )

        val page = safeYtmCall("importYTMLikedAlbums") {
            YouTubeRequestThrottler.run {
                Innertube.library("FEmusic_liked_albums").completed()
            }
        } ?: return false

        runCatching {
            val ytmAlbums = page.items.filterIsInstance<Innertube.AlbumItem>()

            println("YTM albums: $ytmAlbums")

            ytmAlbums.forEach { remoteAlbum ->
                if (remoteAlbum.key.isBlank() || remoteAlbum.title.isNullOrBlank()) return@forEach
                var localAlbum = Database.albumTable.findById( remoteAlbum.key ).first()
                println("Local album: $localAlbum")
                println("Remote album: $remoteAlbum")

                if (localAlbum == null) {
                    localAlbum = Album(
                        id = remoteAlbum.key,
                        title = remoteAlbum.title,
                        thumbnailUrl = remoteAlbum.thumbnail?.url,
                        bookmarkedAt = System.currentTimeMillis(),
                        year = remoteAlbum.year,
                        authorsText = remoteAlbum.authors?.getOrNull(1)?.name,
                        isYoutubeAlbum = true
                    )
                    Database.albumTable.upsert( localAlbum )
                } else {
                    localAlbum.copy(
                        isYoutubeAlbum = true,
                        bookmarkedAt = localAlbum.bookmarkedAt ?: System.currentTimeMillis(),
                        thumbnailUrl = remoteAlbum.thumbnail?.url)
                        .let( Database.albumTable::updateReplace )
                }
            }

            Database.albumTable
                    .all()
                    .first()
                    .filter { album ->
                        album.isYoutubeAlbum && album.id !in ytmAlbums.map { it.key }
                    }
                    .map { it.copy( isYoutubeAlbum = false, bookmarkedAt = null ) }
                    .also( Database.albumTable::updateReplace )
        }.onFailure {
            println("Error importing YTM liked albums: ${it.stackTraceToString()}")
            Timber.e(it, "importYTMLikedAlbums failed")
            return false
        }
        return true
    } else
        return false
}

suspend fun importYTMLikedPlaylists(): Boolean {
    println("importYTMLikedPlaylists isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()} and isAutoSyncEnabled() = ${isAutoSyncEnabled()}")
    if (!isYouTubeSyncEnabled()) return false
    YouTubeSessionStore.applyCurrentSession()

    Toaster.n(R.string.syncing, Toast.LENGTH_LONG)

    val firstPage = safeYtmCall("importYTMLikedPlaylists:first_page") {
        YouTubeRequestThrottler.run {
            Innertube.library("FEmusic_liked_playlists").completed()
        }
    } ?: return false

    val ytmPlaylists = mutableListOf<Innertube.PlaylistItem>()
    ytmPlaylists += firstPage.items.filterIsInstance<Innertube.PlaylistItem>()

    var continuation = firstPage.continuation
    while (!continuation.isNullOrBlank()) {
        val currentContinuation = continuation
        val continuationPage = safeYtmCall(
            label = "importYTMLikedPlaylists:continuation",
            timeoutMs = 20_000L
        ) {
            YouTubeRequestThrottler.run {
                Innertube.libraryContinuation(currentContinuation)
            }
        } ?: break

        ytmPlaylists += continuationPage.items.filterIsInstance<Innertube.PlaylistItem>()
        continuation = continuationPage.continuation
    }

    if (ytmPlaylists.isEmpty()) {
        println("YTM liked playlists response was empty, keeping existing local playlists intact")
        return false
    }

    ytmPlaylists.forEach { remotePlaylist ->
        if (remotePlaylist.key.isBlank()) return@forEach
        val browseId = remotePlaylist.key.removePrefix("VL")
        val localPlaylist = Database.playlistTable.findByBrowseId(browseId).first()
        val playlist = (localPlaylist ?: Playlist(
            name = remotePlaylist.title.orEmpty(),
            browseId = browseId,
            isEditable = remotePlaylist.isEditable == true,
            isYoutubePlaylist = true
        )).copy(
            name = remotePlaylist.title.orEmpty(),
            browseId = browseId,
            isEditable = remotePlaylist.isEditable == true,
            isYoutubePlaylist = true
        )

        val playlistId = if (playlist.id > 0) {
            Database.playlistTable.update(playlist)
            playlist.id
        } else {
            Database.playlistTable.insert(playlist)
        }

        val remoteSongs = safeYtmCall(
            label = "importYTMLikedPlaylists:playlist:$browseId",
            timeoutMs = 30_000L
        ) {
            YtMusic.getPlaylist(browseId).completed()
        }
            ?.songs
            ?.map { it.asNormalizedSong() }
            ?.filter { song -> song.id.isNotBlank() && song.title.isNotBlank() }
            ?.distinctBy { song -> song.id }

        if (remoteSongs.isNullOrEmpty()) {
            println("Playlist sync skipped song remap for $browseId because remote song list was empty")
            return@forEach
        }

        runCatching {
            Database.asyncTransaction {
                val currentPlaylist = playlist.copy(id = playlistId)
                playlistTable.insertIgnore(currentPlaylist)
                playlistTable.update(currentPlaylist)
                remoteSongs.forEach(songTable::insertIgnore)
                songPlaylistMapTable.clear(currentPlaylist.id)
                songPlaylistMapTable.updateReplace(
                    remoteSongs.mapIndexed { index, song ->
                        app.it.fast4x.rimusic.models.SongPlaylistMap(
                            songId = song.id,
                            playlistId = currentPlaylist.id,
                            position = index
                        )
                    }
                )
            }
        }.onFailure {
            Timber.e(it, "importYTMLikedPlaylists failed while saving playlist=%s", browseId)
        }
    }

    Database.playlistTable
        .allAsPreview()
        .first()
        .map { it.playlist }
        .filter { playlist ->
            playlist.isYoutubePlaylist && playlist.browseId !in ytmPlaylists.map { it.key.removePrefix("VL") }
        }
        .forEach { playlist ->
            Database.playlistTable.update(
                playlist.copy(
                    isYoutubePlaylist = false,
                    browseId = null
                )
            )
        }

    return true
}

suspend fun removeYTSongFromPlaylist(
    songId: String,
    playlistBrowseId: String,
    playlistId: Long,
): Boolean {
    println("removeYTSongFromPlaylist removeSongFromPlaylist params songId = $songId, playlistBrowseId = $playlistBrowseId, playlistId = $playlistId")

    if ( isYouTubeSyncEnabled() )  {
        val setVideoId: String = Database.songPlaylistMapTable
                                         .findById( songId, playlistId )
                                         .first()
                                         ?.setVideoId ?: return false

        println("removeYTSongFromPlaylist removeSongFromPlaylist songSetVideoId = $setVideoId")

        YtMusic.removeFromPlaylist( playlistBrowseId, songId, setVideoId )
    }

    return isYouTubeSyncEnabled()
}


@Composable
fun autoSyncToolbutton(messageId: Int): MenuIcon = object : MenuIcon, DynamicColor, Descriptive {

    override var isFirstColor: Boolean by rememberPreference(autosyncKey, false)
    override val iconId: Int = R.drawable.sync
    override val messageId: Int = messageId
    override val menuIconTitle: String
        @Composable
        get() = stringResource(messageId)

    override fun onShortClick() {
        isFirstColor = !isFirstColor
    }
}
