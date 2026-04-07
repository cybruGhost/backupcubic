package app.it.fast4x.rimusic.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.it.fast4x.rimusic.ui.bars.DefaultBottomBar
import app.it.fast4x.rimusic.ui.screens.AlbumScreen
import app.it.fast4x.rimusic.ui.screens.ArtistsScreen
import app.it.fast4x.rimusic.ui.screens.MoodScreen
import app.it.fast4x.rimusic.ui.screens.PlaylistScreen
import app.it.fast4x.rimusic.ui.screens.QuickPicsScreen
import database.entities.Song
import it.fast4x.invidious.Invidious
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import rimusic.composeapp.generated.resources.Res
import rimusic.composeapp.generated.resources.app_icon
import rimusic.composeapp.generated.resources.app_logo_text
import rimusic.composeapp.generated.resources.musical_notes
import vlcj.VlcjController

private data class DesktopNavDestination(
    val route: String,
    val title: String,
)

private val desktopDestinations = listOf(
    DesktopNavDestination("quickpics", "Cubic Picks"),
    DesktopNavDestination("artists", "Artists"),
)

@Composable
fun DesktopApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "quickpics"
    val controller = remember { VlcjController() }
    val scope = rememberCoroutineScope()

    var currentSong by remember { mutableStateOf<Song?>(null) }
    var activeStreamUrl by remember { mutableStateOf<String?>(null) }
    var playbackMessage by remember { mutableStateOf<String?>(null) }
    var isResolvingPlayback by remember { mutableStateOf(false) }
    var selectedMood by remember { mutableStateOf<Innertube.Mood.Item?>(null) }
    var selectedAlbumId by remember { mutableStateOf<String?>(null) }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            controller.dispose()
        }
    }

    LaunchedEffect(activeStreamUrl) {
        val streamUrl = activeStreamUrl ?: return@LaunchedEffect
        controller.load(streamUrl)
        controller.play()
    }

    fun playSong(song: Song) {
        scope.launch {
            currentSong = song
            playbackMessage = null
            isResolvingPlayback = true
            activeStreamUrl = null

            val resolvedUrl = resolveDesktopPlaybackUrl(song.id)
            if (resolvedUrl != null) {
                activeStreamUrl = resolvedUrl
                playbackMessage = "Playing"
            } else {
                playbackMessage = "Couldn't resolve a playable stream for ${song.title}"
            }

            isResolvingPlayback = false
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF07111A)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF0A1622),
                                Color(0xFF0E1D2B),
                                Color(0xFF081018)
                            )
                        )
                    )
            ) {
                DesktopTopBar(
                    currentRoute = currentRoute,
                    canGoBack = currentRoute !in desktopDestinations.map { it.route },
                    onBack = { navController.popBackStack() }
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    DesktopSidebar(
                        currentRoute = currentRoute,
                        onNavigate = {
                            navController.navigate(it) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier
                            .width(220.dp)
                            .fillMaxHeight()
                    )

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(28.dp),
                        color = Color(0xAA0F1A24)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp)
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = "quickpics",
                                modifier = Modifier.fillMaxSize()
                            ) {
                                composable("quickpics") {
                                    QuickPicsScreen(
                                        onSongClick = ::playSong,
                                        onAlbumClick = { browseId ->
                                            selectedAlbumId = browseId
                                            navController.navigate("album")
                                        },
                                        onArtistClick = {
                                            navController.navigate("artists")
                                        },
                                        onPlaylistClick = { browseId ->
                                            selectedPlaylistId = browseId
                                            navController.navigate("playlist")
                                        },
                                        onMoodClick = { mood ->
                                            selectedMood = mood
                                            navController.navigate("mood")
                                        }
                                    )
                                }

                                composable("artists") {
                                    ArtistsScreen(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp)
                                    )
                                }

                                composable("album") {
                                    val browseId = selectedAlbumId.orEmpty()
                                    AlbumScreen(
                                        browseId = browseId,
                                        onSongClick = ::playSong,
                                        onAlbumClick = { nestedBrowseId: String ->
                                            selectedAlbumId = nestedBrowseId
                                            navController.navigate("album")
                                        }
                                    )
                                }
                                composable("playlist") {
                                    val browseId = selectedPlaylistId.orEmpty()
                                    PlaylistScreen(
                                        browseId = browseId,
                                        onSongClick = ::playSong,
                                        onAlbumClick = { albumId: String ->
                                            selectedAlbumId = albumId
                                            navController.navigate("album")
                                        },
                                        onClosePage = { navController.popBackStack() }
                                    )
                                }

                                composable("mood") {
                                    val mood = selectedMood
                                    if (mood != null) {
                                        MoodScreen(
                                            mood = mood,
                                            onAlbumClick = { albumId: String ->
                                                selectedAlbumId = albumId
                                                navController.navigate("album")
                                            },
                                            onArtistClick = {
                                                navController.navigate("artists")
                                            },
                                            onPlaylistClick = { playlistId: String ->
                                                selectedPlaylistId = playlistId
                                                navController.navigate("playlist")
                                            }
                                        )
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("No mood selected", color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                DesktopPlaybackRail(
                    controller = controller,
                    currentSong = currentSong,
                    isResolvingPlayback = isResolvingPlayback,
                    playbackMessage = playbackMessage,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun DesktopTopBar(
    currentRoute: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 18.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xAA122130)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (canGoBack) {
                    Text(
                        text = "Back",
                        color = Color(0xFF9EE6B4),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onBack)
                    )
                }
                androidx.compose.foundation.Image(
                    painter = painterResource(Res.drawable.app_icon),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(40.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(Res.drawable.app_logo_text),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.width(112.dp)
                    )
                    Text(
                        text = when (currentRoute) {
                            "album" -> "Album"
                            "playlist" -> "Playlist"
                            "mood" -> "Mood"
                            "artists" -> "Artists"
                            else -> "Home"
                        },
                        color = Color(0xFFB8CAD9),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text(
                text = "Desktop",
                color = Color(0xFF9EE6B4),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DesktopSidebar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        color = Color(0xA80C151F)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            desktopDestinations.forEach { destination ->
                val isSelected = destination.route == currentRoute
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) Color(0xFF173E5B) else Color.Transparent,
                            RoundedCornerShape(18.dp)
                        )
                        .clickable {
                            if (currentRoute != destination.route) {
                                onNavigate(destination.route)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = destination.title,
                        color = if (isSelected) Color.White else Color(0xFFB7C9D7),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Using the real desktop screen files for home content.",
                color = Color(0xFF88A2B8),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun DesktopPlaybackRail(
    controller: VlcjController,
    currentSong: Song?,
    isResolvingPlayback: Boolean,
    playbackMessage: String?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 0.dp)
            .padding(bottom = 18.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xBB101A23)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(Res.drawable.musical_notes),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color(0xFF90E0C0)),
                    modifier = Modifier.size(22.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = currentSong?.title ?: "Choose something from Cubic Picks",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            isResolvingPlayback -> "Resolving desktop stream..."
                            !playbackMessage.isNullOrBlank() -> playbackMessage
                            currentSong?.artistsText.isNullOrBlank() -> "Ready"
                            else -> currentSong?.artistsText.orEmpty()
                        },
                        color = if (!playbackMessage.isNullOrBlank()) Color(0xFFFFB4AB) else Color(0xFF9DB3C6),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            DefaultBottomBar(controller = controller)
        }
    }
}

private suspend fun resolveDesktopPlaybackUrl(videoId: String): String? = withContext(Dispatchers.IO) {
    val primaryUrl = runCatching {
        Innertube.player(videoId = videoId)
            ?.getOrNull()
            ?.streamingData
            ?.autoMaxQualityFormat
            ?.let { format ->
                if (format.contentLength != null) {
                    "${format.url}&range=0-${format.contentLength}"
                } else {
                    "${format.url}&range=0-10000000"
                }
            }
    }.getOrNull()

    if (!primaryUrl.isNullOrBlank()) return@withContext primaryUrl

    runCatching {
        Invidious.api.videos(videoId)
            ?.getOrNull()
            ?.adaptiveFormats
            .orEmpty()
            .asSequence()
            .mapNotNull { format ->
                val url = readProperty<String>(format, "url")
                val mimeType = readProperty<String>(format, "type")
                    ?: readProperty<String>(format, "mimeType")
                val bitrate = readProperty<Int>(format, "bitrate") ?: 0
                if (url.isNullOrBlank() || (mimeType?.contains("audio", ignoreCase = true) == false)) {
                    null
                } else {
                    bitrate to url
                }
            }
            .maxByOrNull { it.first }
            ?.second
    }.getOrNull()
}

private inline fun <reified T> readProperty(instance: Any, propertyName: String): T? = runCatching {
    val getterName = "get" + propertyName.replaceFirstChar { it.uppercase() }
    val getter = instance.javaClass.methods.firstOrNull { method ->
        method.parameterCount == 0 && (method.name == getterName || method.name == propertyName)
    }
    when {
        getter != null -> getter.invoke(instance) as? T
        else -> instance.javaClass.fields
            .firstOrNull { field -> field.name == propertyName }
            ?.get(instance) as? T
    }
}.getOrNull()
