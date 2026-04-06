package app.it.fast4x.rimusic.utils

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.kreate.android.R
import app.it.fast4x.rimusic.extensions.youtubelogin.YtmSessionApi
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import app.it.fast4x.rimusic.cleanPrefix
import timber.log.Timber

private val ytmAlbumThumbnailCache = mutableMapOf<String, String?>()
private var lastFullAccountSyncAtMs = 0L

private data class SessionLibraryScope(
    val cookie: String,
    val authUser: String?,
    val pageId: String?
)

private fun playlistIdCandidates(vararg ids: String): List<String> =
    ids.asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .flatMap { id ->
            sequenceOf(
                id,
                id.removePrefix("VL"),
                "VL${id.removePrefix("VL")}"
            )
        }
        .distinct()
        .toList()

private fun playlistIdentityKey(playlistId: String, title: String): String =
    playlistId.removePrefix("VL").trim().ifBlank { title.trim().lowercase() }

private fun normalizedPlaylistName(name: String): String =
    cleanPrefix(name).trim().lowercase()

private fun parseRemoteSongCount(songCount: String, subtitle: String): Int {
    val direct = songCount.trim().toIntOrNull()
    if (direct != null) return direct
    return Regex("(\\d+)").find(subtitle)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
}

private suspend fun findExistingYtmPlaylist(
    browseId: String,
    title: String
): Playlist? {
    val allPlaylists = Database.playlistTable
        .allAsPreview()
        .first()
        .map { it.playlist }

    val candidateBrowseIds = playlistIdCandidates(browseId)

    return allPlaylists.firstOrNull { playlist ->
        playlist.isYoutubePlaylist &&
            !playlist.browseId.isNullOrBlank() &&
            candidateBrowseIds.contains(playlist.browseId.trim())
    } ?: allPlaylists.firstOrNull { playlist ->
        playlist.isYoutubePlaylist && normalizedPlaylistName(playlist.name) == normalizedPlaylistName(title)
    }
}

private suspend fun cleanupDuplicateYtmPlaylists() {
    Database.playlistTable
        .allAsPreview()
        .first()
        .filter { it.playlist.isYoutubePlaylist }
        .groupBy { preview ->
            preview.playlist.browseId?.takeIf { it.isNotBlank() }
                ?: normalizedPlaylistName(preview.playlist.name)
        }
        .values
        .forEach { duplicates ->
            duplicates
                .sortedWith(
                    compareByDescending<app.it.fast4x.rimusic.models.PlaylistPreview> { it.songCount > 0 }
                        .thenByDescending { it.playlist.browseId?.isNotBlank() == true }
                        .thenBy { it.playlist.id }
                )
                .drop(1)
                .forEach { duplicate ->
                    Database.songPlaylistMapTable.clear(duplicate.playlist.id)
                    Database.playlistTable.delete(duplicate.playlist)
                }
        }
}

private suspend fun fetchRemotePlaylistSongs(
    sessionScope: SessionLibraryScope?,
    playlistId: String,
    browseId: String,
    timeoutMs: Long = 12_000L
): List<app.it.fast4x.rimusic.models.Song> {
    val isLikedMusicPlaylist = playlistId.equals("LM", ignoreCase = true) || browseId.equals("LM", ignoreCase = true)
    val sessionSongs = sessionScope?.let { scope ->
        val directSongs = if (isLikedMusicPlaylist) {
            safeYtmCall(
                label = "sessionLikedSongs:LM",
                timeoutMs = timeoutMs
            ) {
                YtmSessionApi.fetchLikedSongs(
                    cookies = scope.cookie,
                    authUser = scope.authUser,
                    pageId = scope.pageId
                )
            }?.takeIf { it.isNotEmpty() }
        } else {
            playlistIdCandidates(playlistId, browseId)
                .firstNotNullOfOrNull { candidatePlaylistId ->
                    safeYtmCall(
                        label = "sessionPlaylistSongs:$candidatePlaylistId",
                        timeoutMs = timeoutMs
                    ) {
                        YtmSessionApi.fetchPlaylistSongs(
                            cookies = scope.cookie,
                            playlistId = candidatePlaylistId,
                            authUser = scope.authUser,
                            pageId = scope.pageId
                        )
                    }?.takeIf { it.isNotEmpty() }
                }
        }

        directSongs?.map { remoteSong ->
                app.it.fast4x.rimusic.models.Song(
                    id = remoteSong.videoId,
                    title = cleanPrefix(remoteSong.title),
                    artistsText = remoteSong.artist.ifBlank { null },
                    durationText = remoteSong.duration.ifBlank { null },
                    thumbnailUrl = remoteSong.thumbnail.ifBlank { null },
                    totalPlayTimeMs = 1L
                )
            }
    }

    if (!sessionSongs.isNullOrEmpty()) {
        return sessionSongs
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .distinctBy { it.id }
    }

    return safeYtmCall(
        label = "ytmPlaylistSongs:$browseId",
        timeoutMs = timeoutMs
    ) {
        YtMusic.getPlaylist(playlistId = browseId).completed()
    }?.songs
        ?.map { it.asNormalizedSong() }
        ?.filter { it.id.isNotBlank() && it.title.isNotBlank() }
        ?.distinctBy { it.id }
        ?.takeIf { it.isNotEmpty() }
        ?: playlistIdCandidates(browseId)
            .firstNotNullOfOrNull { candidateBrowseId ->
                safeYtmCall(
                    label = "ytmPlaylistSongsCandidate:$candidateBrowseId",
                    timeoutMs = timeoutMs,
                    maxRetries = 1
                ) {
                    YtMusic.getPlaylist(playlistId = candidateBrowseId).completed()
                }?.songs
                    ?.map { it.asNormalizedSong() }
                    ?.filter { it.id.isNotBlank() && it.title.isNotBlank() }
                    ?.distinctBy { it.id }
                    ?.takeIf { it.isNotEmpty() }
            }
        .orEmpty()
}

private fun currentSessionLibraryScope(): SessionLibraryScope? {
    val session = YouTubeSessionStore.applyCurrentSession() ?: return null
    val cookie = session.cookie.takeIf { it.isNotBlank() } ?: return null
    return SessionLibraryScope(
        cookie = cookie,
        authUser = session.authUser.ifBlank { null },
        pageId = session.pageId.ifBlank { null }
    )
}

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
    timeoutMs: Long = 10_000L,
    maxRetries: Int = 1,
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

private suspend fun syncSessionPlaylists(scope: SessionLibraryScope): Boolean =
    withTimeoutOrNull(60_000L) {
        val remotePlaylists = safeYtmCall("sessionPlaylists") {
            YtmSessionApi.fetchPlaylists(scope.cookie, scope.authUser, scope.pageId)
        }?.distinctBy { playlist ->
            playlistIdentityKey(playlist.playlistId, playlist.title)
        } ?: return@withTimeoutOrNull false

        if (remotePlaylists.isEmpty()) return@withTimeoutOrNull false

        val syncedBrowseIds = mutableSetOf<String>()
        remotePlaylists.forEachIndexed { index, remotePlaylist ->
            kotlinx.coroutines.currentCoroutineContext().ensureActive()

            if (remotePlaylist.playlistId.isBlank() || remotePlaylist.title.isBlank()) {
                return@forEachIndexed
            }

            val remoteBrowseId = remotePlaylist.playlistId.trim()
            val localPlaylist = findExistingYtmPlaylist(
                browseId = remoteBrowseId,
                title = remotePlaylist.title
            )
            syncedBrowseIds += remoteBrowseId
            val playlist = (localPlaylist ?: Playlist(
                name = remotePlaylist.title,
                browseId = remoteBrowseId,
                isYoutubePlaylist = true
            )).copy(
                name = remotePlaylist.title,
                browseId = remoteBrowseId,
                isYoutubePlaylist = true
            )
            val localSongCount = if ((localPlaylist?.id ?: 0) > 0) {
                Database.songPlaylistMapTable.allSongsOf(localPlaylist!!.id).first().size
            } else {
                0
            }
            val remoteSongCount = parseRemoteSongCount(remotePlaylist.songCount, remotePlaylist.subtitle)
            val shouldFetchSongs = localSongCount <= 0 || (remoteSongCount > 0 && localSongCount != remoteSongCount)

            if (index > 0 && shouldFetchSongs) {
                delay(650L)
            }

            val remoteSongs = if (!shouldFetchSongs) {
                emptyList()
            } else {
                fetchRemotePlaylistSongs(
                    sessionScope = scope,
                    playlistId = remotePlaylist.playlistId,
                    browseId = remoteBrowseId,
                    timeoutMs = 12_000L
                )
            }

            Database.asyncTransaction {
                val playlistId = if (playlist.id > 0) {
                    playlistTable.update(playlist)
                    playlist.id
                } else {
                    playlistTable.insert(playlist)
                }

                if (remoteSongs.isNotEmpty()) {
                    remoteSongs.forEach(songTable::insertIgnore)
                    songPlaylistMapTable.clear(playlistId)
                    songPlaylistMapTable.updateReplace(
                        remoteSongs.mapIndexed { songIndex, song ->
                            app.it.fast4x.rimusic.models.SongPlaylistMap(
                                songId = song.id,
                                playlistId = playlistId,
                                position = songIndex
                            )
                        }
                    )
                }
            }
        }

        Database.playlistTable
            .allAsPreview()
            .first()
            .map { it.playlist }
            .filter { playlist ->
                playlist.isYoutubePlaylist && playlist.browseId !in syncedBrowseIds
            }
            .forEach { playlist ->
                Database.playlistTable.update(
                    playlist.copy(
                        isYoutubePlaylist = false,
                        browseId = null
                    )
                )
            }

        cleanupDuplicateYtmPlaylists()

        true
    } ?: false

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
        val sessionScope = currentSessionLibraryScope()

    val sessionArtists = sessionScope?.let { scope ->
        safeYtmCall("sessionArtists") {
            YtmSessionApi.fetchArtists(scope.cookie, scope.authUser, scope.pageId)
            }
        }
        if (!sessionArtists.isNullOrEmpty()) {
            runCatching {
                sessionArtists.forEach { remoteArtist ->
                    if (remoteArtist.browseId.isBlank() || remoteArtist.name.isBlank()) return@forEach
                    val localArtist = Database.artistTable.findById(remoteArtist.browseId).first()
                    if (localArtist == null) {
                        Database.artistTable.upsert(
                            Artist(
                                id = remoteArtist.browseId,
                                name = remoteArtist.name,
                                thumbnailUrl = remoteArtist.thumbnail,
                                bookmarkedAt = System.currentTimeMillis(),
                                isYoutubeArtist = true
                            )
                        )
                    } else {
                        Database.artistTable.update(
                            localArtist.copy(
                                name = remoteArtist.name,
                                thumbnailUrl = remoteArtist.thumbnail,
                                bookmarkedAt = localArtist.bookmarkedAt ?: System.currentTimeMillis(),
                                isYoutubeArtist = true
                            )
                        )
                    }
                }
            }.onSuccess { return true }
        }

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
        val sessionScope = currentSessionLibraryScope()

        val sessionAlbums = sessionScope?.let { scope ->
            safeYtmCall("sessionAlbums") {
                YtmSessionApi.fetchAlbums(scope.cookie, scope.authUser, scope.pageId)
            }
        }
        if (!sessionAlbums.isNullOrEmpty()) {
            runCatching {
                sessionAlbums.forEach { remoteAlbum ->
                    if (remoteAlbum.browseId.isBlank() || remoteAlbum.title.isBlank()) return@forEach
                    val localAlbum = Database.albumTable.findById(remoteAlbum.browseId).first()
                    val album = (localAlbum ?: Album(
                        id = remoteAlbum.browseId,
                        title = remoteAlbum.title,
                        authorsText = remoteAlbum.artist,
                        thumbnailUrl = remoteAlbum.thumbnail,
                        year = remoteAlbum.year,
                        bookmarkedAt = System.currentTimeMillis(),
                        isYoutubeAlbum = true
                    )).copy(
                        title = remoteAlbum.title,
                        authorsText = remoteAlbum.artist,
                        thumbnailUrl = remoteAlbum.thumbnail,
                        year = remoteAlbum.year,
                        bookmarkedAt = localAlbum?.bookmarkedAt ?: System.currentTimeMillis(),
                        isYoutubeAlbum = true
                    )
                    if (localAlbum == null) Database.albumTable.upsert(album) else Database.albumTable.updateReplace(album)
                }
            }.onSuccess { return true }
        }

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
    val sessionScope = currentSessionLibraryScope()

    if (sessionScope != null && syncSessionPlaylists(sessionScope)) {
        return true
    }

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

    val distinctYtmPlaylists = ytmPlaylists.distinctBy { playlist ->
        playlistIdentityKey(playlist.key, playlist.title.orEmpty())
    }

    distinctYtmPlaylists.forEach { remotePlaylist ->
        if (remotePlaylist.key.isBlank()) return@forEach
        val browseId = remotePlaylist.key.removePrefix("VL")
        val localPlaylist = findExistingYtmPlaylist(
            browseId = remotePlaylist.key,
            title = remotePlaylist.title.orEmpty()
        )
        val playlist = (localPlaylist ?: Playlist(
            name = remotePlaylist.title.orEmpty(),
            browseId = remotePlaylist.key,
            isEditable = remotePlaylist.isEditable == true,
            isYoutubePlaylist = true
        )).copy(
            name = remotePlaylist.title.orEmpty(),
            browseId = remotePlaylist.key,
            isEditable = remotePlaylist.isEditable == true,
            isYoutubePlaylist = true
        )
        val localSongCount = if ((localPlaylist?.id ?: 0) > 0) {
            Database.songPlaylistMapTable.allSongsOf(localPlaylist!!.id).first().size
        } else {
            0
        }

        val playlistId = if (playlist.id > 0) {
            Database.playlistTable.update(playlist)
            playlist.id
        } else {
            Database.playlistTable.insert(playlist)
        }

        val remoteSongs = if (localSongCount > 0) {
            emptyList()
        } else {
            fetchRemotePlaylistSongs(
                sessionScope = sessionScope,
                playlistId = remotePlaylist.key,
                browseId = remotePlaylist.key,
                timeoutMs = 30_000L
            )
        }

        if (remoteSongs.isEmpty() && localSongCount <= 0) {
            println("Playlist sync skipped song remap for $browseId because remote song list was empty")
            return@forEach
        }

        runCatching {
            Database.asyncTransaction {
                val currentPlaylist = playlist.copy(id = playlistId)
                playlistTable.insertIgnore(currentPlaylist)
                playlistTable.update(currentPlaylist)
                if (remoteSongs.isNotEmpty()) {
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
            playlist.isYoutubePlaylist && playlist.browseId !in distinctYtmPlaylists.map { it.key.removePrefix("VL") }
        }
        .forEach { playlist ->
            Database.playlistTable.update(
                playlist.copy(
                    isYoutubePlaylist = false,
                    browseId = null
                )
            )
        }

    cleanupDuplicateYtmPlaylists()

    return true
}

suspend fun importYTMLikedSongs(): Boolean {
    if (!isYouTubeSyncEnabled()) return false

    val sessionScope = currentSessionLibraryScope() ?: return false
    val sessionSongs = safeYtmCall("sessionLikedSongs") {
        YtmSessionApi.fetchLikedSongs(sessionScope.cookie, sessionScope.authUser, sessionScope.pageId)
    }
    val fallbackSongs = if (sessionSongs.isNullOrEmpty()) {
        fetchRemotePlaylistSongs(
            sessionScope = sessionScope,
            playlistId = "LM",
            browseId = "LM"
        )
    } else {
        emptyList()
    }

    if (sessionSongs.isNullOrEmpty() && fallbackSongs.isEmpty()) return false

    runCatching {
        if (!sessionSongs.isNullOrEmpty()) {
            sessionSongs.forEach { remoteSong ->
                if (remoteSong.videoId.isBlank() || remoteSong.title.isBlank()) return@forEach

                val localSong = Database.songTable.findById(remoteSong.videoId).first()
                val normalizedSong = (localSong ?: app.it.fast4x.rimusic.models.Song(
                    id = remoteSong.videoId,
                    title = cleanPrefix(remoteSong.title),
                    artistsText = remoteSong.artist.ifBlank { null },
                    durationText = remoteSong.duration.ifBlank { null },
                    thumbnailUrl = remoteSong.thumbnail.ifBlank { null }
                )).copy(
                    title = cleanPrefix(remoteSong.title),
                    artistsText = remoteSong.artist.ifBlank { localSong?.artistsText },
                    durationText = remoteSong.duration.ifBlank { localSong?.durationText },
                    thumbnailUrl = remoteSong.thumbnail.ifBlank { localSong?.thumbnailUrl },
                    likedAt = localSong?.likedAt?.takeIf { it > 0 } ?: System.currentTimeMillis()
                )

                Database.songTable.upsert(normalizedSong)
            }
        } else {
            fallbackSongs.forEach { song ->
                val localSong = Database.songTable.findById(song.id).first()
                Database.songTable.upsert(
                    (localSong ?: song).copy(
                        title = cleanPrefix((localSong ?: song).title),
                        likedAt = localSong?.likedAt?.takeIf { it > 0 } ?: System.currentTimeMillis()
                    )
                )
            }
        }
    }.onFailure {
        Timber.e(it, "importYTMLikedSongs failed")
        return false
    }

    return true
}

suspend fun syncSelectedYtmAccountData(): Boolean {
    if (!isYouTubeSyncEnabled()) return false
    val now = System.currentTimeMillis()
    if (now - lastFullAccountSyncAtMs < 60_000L) {
        return false
    }

    val syncSteps = listOf(
        "liked songs" to ::importYTMLikedSongs,
        "playlists" to ::importYTMLikedPlaylists,
        "artists" to ::importYTMSubscribedChannels,
        "albums" to ::importYTMLikedAlbums
    )

    var syncedAny = false
    syncSteps.forEach { (label, syncStep) ->
        runCatching {
            syncStep()
        }.onSuccess { stepSucceeded ->
            syncedAny = syncedAny || stepSucceeded
        }.onFailure { error ->
            Timber.e(error, "syncSelectedYtmAccountData failed while syncing %s", label)
        }
    }

    if (syncedAny) {
        lastFullAccountSyncAtMs = now
    }

    return syncedAny
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
