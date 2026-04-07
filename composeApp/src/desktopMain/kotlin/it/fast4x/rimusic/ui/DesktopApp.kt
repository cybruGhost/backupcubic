package app.it.fast4x.rimusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ktor.client.call.body
import app.it.fast4x.rimusic.ui.pages.AlbumsPage
import app.it.fast4x.rimusic.ui.pages.SongsPage
import app.it.fast4x.rimusic.ui.screens.ArtistsScreen
import app.it.fast4x.rimusic.utils.LoadImage
import database.DB
import database.entities.Album
import database.entities.Song
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.BrowseResponse
import it.fast4x.innertube.models.getContinuation
import it.fast4x.innertube.requests.HomePage
import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.Image
import app.it.fast4x.rimusic.ui.theme.DesktopTheme
import rimusic.composeapp.generated.resources.Res
import rimusic.composeapp.generated.resources.app_icon

private data class DesktopNavItem(
    val route: String,
    val title: String,
    val badge: String? = null,
)

private object DesktopHomePageCache {
    private val pages = mutableMapOf<String, HomePage>()

    fun get(params: String?): HomePage? = pages[params.orEmpty()]

    fun put(params: String?, page: HomePage) {
        pages[params.orEmpty()] = page
    }

    fun clear(params: String? = null) {
        if (params == null) {
            pages.clear()
        } else {
            pages.remove(params.orEmpty())
        }
    }
}

@Composable
fun DesktopApp() {
    var currentRoute by remember { mutableStateOf("home") }
    var nowPlaying by remember { mutableStateOf<DesktopNowPlaying?>(null) }
    val playQueue = remember { mutableListOf<DesktopNowPlaying>().toMutableStateList() }

    DesktopTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF0B1117),
                                Color(0xFF0D141C),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            ) {
                DesktopSidebar(
                    currentRoute = currentRoute,
                    onNavigate = { currentRoute = it },
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(260.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DesktopHeader(currentRoute = currentRoute)

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = Color(0xCC121A24),
                        tonalElevation = 8.dp,
                        shadowElevation = 12.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp)
                        ) {
                            DesktopRouteContent(
                                route = currentRoute,
                                onNowPlaying = {
                                    nowPlaying = it
                                    playQueue.removeAll { queued -> queued.title == it.title && queued.subtitle == it.subtitle }
                                    playQueue.add(0, it)
                                    if (playQueue.size > 12) {
                                        playQueue.removeLast()
                                    }
                                }
                            )
                        }
                    }

                    DesktopPlayerDock(
                        nowPlaying = nowPlaying,
                        onClear = { nowPlaying = null }
                    )
                }

                DesktopRightRail(
                    nowPlaying = nowPlaying,
                    queue = playQueue,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(320.dp)
                        .padding(start = 0.dp, top = 18.dp, end = 18.dp, bottom = 18.dp)
                )
            }
        }
    }
}

@Composable
private fun DesktopHeader(currentRoute: String) {
    val subtitle = when (currentRoute) {
        "home" -> "Innertube-powered home feed with real YouTube Music sections"
        "library" -> "Shared database content surfaced for desktop playback"
        "songs" -> "Song library from the shared local database"
        "albums" -> "Album library from the shared local database"
        "artists" -> "Artist browsing view"
        "settings" -> "Desktop preferences and build information"
        else -> "Desktop shell"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xB81A2430),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = currentRoute.toDesktopTitle(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB2C1CF)
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0x2237D67A),
                modifier = Modifier.border(1.dp, Color(0x4437D67A), RoundedCornerShape(999.dp))
            ) {
                Text(
                    text = "Desktop Beta",
                    color = Color(0xFF9BE8B7),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun DesktopSidebar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val navItems = listOf(
        DesktopNavItem("home", "Home"),
        DesktopNavItem("library", "Library"),
        DesktopNavItem("songs", "Songs"),
        DesktopNavItem("albums", "Albums"),
        DesktopNavItem("artists", "Artists"),
        DesktopNavItem("settings", "Settings", "Beta")
    )

    Surface(
        modifier = modifier.padding(18.dp),
        shape = RoundedCornerShape(30.dp),
        color = Color(0xFF0A1016),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF102B1D)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(Res.drawable.app_icon),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color(0xFF7FE1A6)),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = "Cubic Music",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Desktop",
                        color = Color(0xFF9CB0C2),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            navItems.forEach { item ->
                val selected = currentRoute == item.route
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) Color(0xFF17324A) else Color.Transparent)
                        .clickable { onNavigate(item.route) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        color = if (selected) Color.White else Color(0xFFB5C2CE),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    item.badge?.let { badge ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) Color(0xFF27506F) else Color(0x1FFFFFFF)
                        ) {
                            Text(
                                text = badge,
                                color = Color(0xFFDCE8F2),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF101923)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Current status",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Desktop home pulls real Innertube sections, and library routes read from the shared database.",
                        color = Color(0xFF9FB0BF),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopRouteContent(
    route: String,
    onNowPlaying: (DesktopNowPlaying) -> Unit
) {
    when (route) {
        "home" -> QuickPicsScreen(
            onNowPlaying = onNowPlaying
        )

        "library" -> DesktopLibraryPage(onNowPlaying = onNowPlaying)

        "songs" -> SongsPage(
            onSongClick = { song ->
                onNowPlaying(DesktopNowPlaying(song.title, song.artistsText.orEmpty()))
            }
        )

        "albums" -> AlbumsPage(
            onAlbumClick = { album ->
                onNowPlaying(DesktopNowPlaying(album.title ?: "Album", album.authorsText ?: "Album"))
            }
        )

        "artists" -> ArtistsScreen(
            modifier = Modifier.fillMaxSize()
        )

        "settings" -> DesktopSettingsPage()

        else -> DesktopEmptyState("This desktop page is not ready yet.")
    }
}

@Composable
private fun QuickPicsScreen(
    onNowPlaying: (DesktopNowPlaying) -> Unit
) {
    var homePage by remember { mutableStateOf(DesktopHomePageCache.get(null)) }
    var isLoading by remember { mutableStateOf(homePage == null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedChipParams by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedChipParams) {
        val cachedPage = DesktopHomePageCache.get(selectedChipParams)
        if (cachedPage != null) {
            homePage = cachedPage
            isLoading = false
            errorMessage = null
            return@LaunchedEffect
        }

        isLoading = true
        errorMessage = null
        homePage = loadInnertubeHomePage(selectedChipParams)
            .onFailure { errorMessage = it.message ?: "Failed to load home feed" }
            .onSuccess { DesktopHomePageCache.put(selectedChipParams, it) }
            .getOrNull()
        isLoading = false
    }

    when {
        isLoading -> DesktopEmptyState("Loading Innertube home...")
        !errorMessage.isNullOrBlank() && homePage == null -> DesktopEmptyState(errorMessage!!)
        else -> {
            val sections = homePage?.sections
                ?.filter { section -> section.title.isNotBlank() && section.items.isNotEmpty() }
                .orEmpty()

            if (sections.isEmpty()) {
                DesktopEmptyState("No home sections were returned.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    homePage?.chips?.takeIf { it.isNotEmpty() }?.let { chips ->
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(chips.take(12)) { chip ->
                                    val isSelected = when {
                                        selectedChipParams != null -> chip.endpoint?.params == selectedChipParams
                                        else -> chip.isSelected
                                    }
                                    DesktopActionChip(
                                        label = chip.title,
                                        selected = isSelected,
                                        onClick = {
                                            selectedChipParams = if (chip.endpoint?.params == selectedChipParams) {
                                                null
                                            } else {
                                                chip.endpoint?.params
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    items(sections.size) { index ->
                        val section = sections[index]
                        DesktopHomeSection(
                            section = section,
                            onNowPlaying = onNowPlaying
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopHomeSection(
    section: HomePage.Section,
    onNowPlaying: (DesktopNowPlaying) -> Unit
) {
    val filteredItems = section.items.filterNotNull()
    val isDailyDiscoverSection = section.title.contains("daily discover", ignoreCase = true)

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = section.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            section.label?.takeIf { it.isNotBlank() }?.let { label ->
                Text(
                    text = label,
                    color = Color(0xFF9FB0BF),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (isDailyDiscoverSection) {
            LazyHorizontalGrid(
                rows = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredItems) { item ->
                    DesktopHomeCard(
                        item = item,
                        onClick = { onNowPlaying(item.asDesktopNowPlaying()) }
                    )
                }
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredItems) { item ->
                    DesktopHomeCard(
                        item = item,
                        onClick = { onNowPlaying(item.asDesktopNowPlaying()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopHomeCard(
    item: Innertube.Item,
    onClick: () -> Unit
) {
    val (title, subtitle, thumbnailUrl, isCircle, width, height) = when (item) {
        is Innertube.SongItem -> DesktopCardData(
            title = item.info?.name.orEmpty(),
            subtitle = item.authors?.joinToString(", ") { it.name.orEmpty() }.orEmpty(),
            thumbnailUrl = item.thumbnail?.url,
            isCircle = false,
            width = 150.dp,
            height = 150.dp
        )

        is Innertube.VideoItem -> DesktopCardData(
            title = item.info?.name.orEmpty(),
            subtitle = item.authors?.joinToString(", ") { it.name.orEmpty() }.orEmpty(),
            thumbnailUrl = item.thumbnail?.url,
            isCircle = false,
            width = 190.dp,
            height = 120.dp
        )

        is Innertube.AlbumItem -> DesktopCardData(
            title = item.info?.name.orEmpty(),
            subtitle = item.authors?.joinToString(", ") { it.name.orEmpty() }.orEmpty(),
            thumbnailUrl = item.thumbnail?.url,
            isCircle = false,
            width = 150.dp,
            height = 150.dp
        )

        is Innertube.ArtistItem -> DesktopCardData(
            title = item.info?.name.orEmpty(),
            subtitle = item.subscribersCountText.orEmpty(),
            thumbnailUrl = item.thumbnail?.url,
            isCircle = true,
            width = 122.dp,
            height = 122.dp
        )

        is Innertube.PlaylistItem -> DesktopCardData(
            title = item.info?.name.orEmpty(),
            subtitle = item.channel?.name.orEmpty(),
            thumbnailUrl = item.thumbnail?.url,
            isCircle = false,
            width = 150.dp,
            height = 150.dp
        )
    }

    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .clip(if (isCircle) CircleShape else RoundedCornerShape(22.dp))
                .background(Color(0xFF101923))
        ) {
            thumbnailUrl?.takeIf { it.isNotBlank() }?.let { LoadImage(it) }
        }

        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        subtitle.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                color = Color(0xFF9FB0BF),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun Innertube.Item.asDesktopNowPlaying(): DesktopNowPlaying = when (this) {
    is Innertube.SongItem -> DesktopNowPlaying(
        title = info?.name.orEmpty(),
        subtitle = authors?.joinToString(", ") { author -> author.name.orEmpty() }.orEmpty()
    )

    is Innertube.VideoItem -> DesktopNowPlaying(
        title = info?.name.orEmpty(),
        subtitle = authors?.joinToString(", ") { author -> author.name.orEmpty() }.orEmpty()
    )

    is Innertube.AlbumItem -> DesktopNowPlaying(
        title = info?.name.orEmpty(),
        subtitle = authors?.joinToString(", ") { author -> author.name.orEmpty() }.orEmpty()
    )

    is Innertube.ArtistItem -> DesktopNowPlaying(
        title = info?.name.orEmpty(),
        subtitle = subscribersCountText.orEmpty()
    )

    is Innertube.PlaylistItem -> DesktopNowPlaying(
        title = info?.name.orEmpty(),
        subtitle = channel?.name.orEmpty()
    )
}

@Composable
private fun DesktopOverviewPage(
    onNowPlaying: (DesktopNowPlaying) -> Unit
) {
    var songCount by remember { mutableStateOf(0) }
    var albumCount by remember { mutableStateOf(0) }
    var latestSong by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(Unit) {
        val songs = DB.getAllSongs().firstOrNull().orEmpty()
        val albums = DB.getAllAlbums().firstOrNull().orEmpty()
        songCount = songs.size
        albumCount = albums.size
        latestSong = songs.firstOrNull()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DesktopMetricCard("Songs", songCount.toString(), Modifier.weight(1f))
                DesktopMetricCard("Albums", albumCount.toString(), Modifier.weight(1f))
                DesktopMetricCard("Theme", "Desktop", Modifier.weight(1f))
            }
        }
        item {
            DesktopSectionCard(
                title = "Welcome back",
                body = "This desktop build now has a real shell built from desktop/common sources. Home, Library, Songs, Albums, Artists, Settings, and a now-playing rail are active."
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xA9192330)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Continue listening",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = latestSong?.title ?: "No desktop library songs found yet",
                        color = Color(0xFFD9E5EF),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = latestSong?.artistsText ?: "Import or sync music to populate this area",
                        color = Color(0xFF9FB0BF),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DesktopActionChip("Play latest") {
                            latestSong?.let { song ->
                                onNowPlaying(DesktopNowPlaying(song.title, song.artistsText.orEmpty()))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopLibraryPage(
    onNowPlaying: (DesktopNowPlaying) -> Unit
) {
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }

    LaunchedEffect(Unit) {
        songs = DB.getAllSongs().firstOrNull().orEmpty()
        albums = DB.getAllAlbums().firstOrNull().orEmpty()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            DesktopSectionCard(
                title = "Your library",
                body = "Desktop library is reading the shared Room database. Tap an item to send it to the player dock."
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DesktopMetricCard("Liked songs", songs.count { it.isLiked }.toString(), Modifier.weight(1f))
                DesktopMetricCard("Bookmarked albums", albums.count { it.bookmarkedAt != null }.toString(), Modifier.weight(1f))
            }
        }
        item {
            DesktopPreviewList(
                title = "Recent songs",
                items = songs.take(6).map { it.title to it.artistsText.orEmpty() },
                onClick = { index ->
                    songs.getOrNull(index)?.let { song ->
                        onNowPlaying(DesktopNowPlaying(song.title, song.artistsText.orEmpty()))
                    }
                }
            )
        }
        item {
            DesktopPreviewList(
                title = "Recent albums",
                items = albums.take(6).map { (it.title ?: "Album") to (it.authorsText ?: "Album") }
            )
        }
    }
}

@Composable
private fun DesktopPlayerDock(
    nowPlaying: DesktopNowPlaying?,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xD9141C26),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2E7D68), Color(0xFF20394D))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (nowPlaying == null) "CM" else "▶",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = nowPlaying?.title ?: "No song selected",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = nowPlaying?.subtitle ?: "Click a song from Home, Songs, or Albums to populate the player dock",
                        color = Color(0xFF9EB0C0),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DesktopActionChip("Queue")
                DesktopActionChip("Lyrics")
                DesktopActionChip("Clear", onClick = onClear)
            }
        }
    }
}

@Composable
private fun DesktopActionChip(
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) Color(0xFF17324A) else Color(0x1FFFFFFF),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun DesktopSettingsPage() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            DesktopSectionCard(
                title = "Desktop settings",
                body = "This desktop surface is using shared code and resources, with the home feed loaded directly through Innertube."
            )
        }
        item {
            DesktopSectionCard(
                title = "Playback status",
                body = "The player dock is currently a desktop shell for selected songs, albums, artists, and playlists."
            )
        }
        item {
            DesktopSectionCard(
                title = "Data sources",
                body = "Home uses Innertube browse data. Library, songs, and albums use the shared local database."
            )
        }
    }
}

@Composable
private fun DesktopRightRail(
    nowPlaying: DesktopNowPlaying?,
    queue: List<DesktopNowPlaying>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        color = Color(0xFF0C131A),
        tonalElevation = 8.dp
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                DesktopSectionCard(
                    title = "Now playing",
                    body = nowPlaying?.let { "${it.title}\n${it.subtitle}" }
                        ?: "Pick a song from Home, Songs, or Albums to send it here."
                )
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF111B25)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Playback",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall
                        )
                        LinearProgressIndicator(
                            progress = { if (nowPlaying == null) 0f else 0.42f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF61D89C),
                            trackColor = Color(0x332E3C4A)
                        )
                        Text(
                            text = if (nowPlaying == null) "Idle" else "Selection loaded into the desktop dock",
                            color = Color(0xFF9FB0BF),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            item {
                DesktopPreviewList(
                    title = "Queue",
                    items = queue.map { it.title to it.subtitle }.ifEmpty {
                        listOf("Queue is empty" to "Desktop selections will appear here")
                    }
                )
            }
        }
    }
}

@Composable
private fun DesktopSectionCard(
    title: String,
    body: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xA9192330)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                color = Color(0xFFB0C0CE),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun DesktopMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color(0xA9192330)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                color = Color(0xFF9EB0BF),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = value,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DesktopPreviewList(
    title: String,
    items: List<Pair<String, String>>,
    onClick: ((Int) -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xA9192330)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            items.forEachIndexed { index, (primary, secondary) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = onClick != null) { onClick?.invoke(index) }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF17324A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = primary,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = secondary,
                            color = Color(0xFF9FB0BF),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopEmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = Color(0xFFB0C0CE),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun String.toDesktopTitle(): String = when (this) {
    "home" -> "Home"
    "library" -> "Library"
    "songs" -> "Songs"
    "albums" -> "Albums"
    "artists" -> "Artists"
    "settings" -> "Settings"
    else -> replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

private data class DesktopNowPlaying(
    val title: String,
    val subtitle: String
)

private data class DesktopCardData(
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val isCircle: Boolean,
    val width: androidx.compose.ui.unit.Dp,
    val height: androidx.compose.ui.unit.Dp
)

private suspend fun loadInnertubeHomePage(params: String? = null): Result<HomePage> = runCatching {
    var response = Innertube.browse(
        browseId = "FEmusic_home",
        params = params,
        setLogin = false
    ).body<BrowseResponse>()

    val sectionListRenderer = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
        ?.tabRenderer?.content?.sectionListRenderer

    val sections = sectionListRenderer?.contents
        ?.mapNotNull { it.musicCarouselShelfRenderer }
        ?.mapNotNull(HomePage.Section::fromMusicCarouselShelfRenderer)
        ?.toMutableList()
        ?: mutableListOf()

    val chips = sectionListRenderer?.header?.chipCloudRenderer?.chips
        ?.mapNotNull(HomePage.Chip::fromChipCloudChipRenderer)

    var continuation = sectionListRenderer?.continuations?.getContinuation()

    while (continuation != null) {
        response = Innertube.browse(continuation = continuation).body()
        continuation = response.continuationContents?.sectionListContinuation?.continuations?.getContinuation()
        sections += response.continuationContents?.sectionListContinuation?.contents
            ?.mapNotNull { it.musicCarouselShelfRenderer }
            ?.mapNotNull(HomePage.Section::fromMusicCarouselShelfRenderer)
            .orEmpty()
    }

    HomePage(
        chips = chips,
        sections = sections.distinctBy { it.title to it.label },
        continuation = continuation
    )
}
