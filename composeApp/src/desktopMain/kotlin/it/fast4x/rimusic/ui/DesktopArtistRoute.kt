package app.it.fast4x.rimusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.components.Loader
import app.it.fast4x.rimusic.ui.components.Title
import app.it.fast4x.rimusic.utils.asSong
import database.entities.Song
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.BrowseBody
import it.fast4x.innertube.requests.artistPage

@Composable
fun DesktopArtistRoute(
    browseId: String,
    onSongClick: (Song) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val artistPage = remember { mutableStateOf<Innertube.ArtistPage?>(null) }
    val isLoading = remember(browseId) { mutableStateOf(true) }

    LaunchedEffect(browseId) {
        isLoading.value = true
        Innertube.artistPage(BrowseBody(browseId = browseId))
            ?.onSuccess { artistPage.value = it }
        isLoading.value = false
    }

    if (isLoading.value) {
        Loader()
        return
    }

    val page = artistPage.value
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Title(title = page?.name ?: "Artist")
        page?.subscriberCountText?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        val songs = page?.songs?.distinctBy { it.key }.orEmpty()
        if (songs.isNotEmpty()) {
            Text("Songs", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            songs.forEach { song ->
                DesktopArtistListRow(
                    title = song.info?.name ?: song.asSong.title,
                    subtitle = song.authors?.joinToString(", ") { it.name ?: "" }.orEmpty(),
                    trailing = song.durationText.orEmpty(),
                    modifier = Modifier.clickable { onSongClick(song.asSong) }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        val albums = page?.albums.orEmpty()
        if (albums.isNotEmpty()) {
            Text("Albums", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            albums.forEach { album ->
                DesktopArtistListRow(
                    title = album.title ?: "",
                    subtitle = album.year ?: "",
                    modifier = Modifier.clickable { album.key?.let(onAlbumClick) }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        val playlists = page?.playlists.orEmpty()
        if (playlists.isNotEmpty()) {
            Text("Playlists", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            playlists.forEach { playlist ->
                DesktopArtistListRow(
                    title = playlist.title ?: "",
                    subtitle = playlist.channel?.name ?: "",
                    modifier = Modifier.clickable { playlist.key?.let(onPlaylistClick) }
                )
            }
        }
    }
}

@Composable
private fun DesktopArtistListRow(
    title: String,
    subtitle: String,
    trailing: String = "",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        if (subtitle.isNotBlank() || trailing.isNotBlank()) {
            val detail = listOf(subtitle, trailing).filter { it.isNotBlank() }.joinToString(" • ")
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
