package app.it.fast4x.rimusic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.it.fast4x.rimusic.ui.bars.DefaultBottomBar
import app.it.fast4x.rimusic.ui.screens.*
import database.entities.Song
import it.fast4x.invidious.Invidious
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import rimusic.composeapp.generated.resources.*
import vlcj.VlcjController

// ── Palette ────────────────────────────────────────────────────────────────────
private object RiColor {
    val bg          = Color(0xFF0D1117)
    val surface     = Color(0xFF161B22)
    val surfaceGlass= Color(0x991A2332)
    val glass       = Color(0x661E2D3D)
    val border      = Color(0xFF21303F)
    val accent      = Color(0xFF1DB954)   // spotify-ish green
    val accentDim   = Color(0xFF14532D)
    val teal        = Color(0xFF0ABFBC)
    val textPrimary = Color(0xFFEEF2FF)
    val textSecond  = Color(0xFF8899AA)
    val textDim     = Color(0xFF445566)
    val red         = Color(0xFFFF6B6B)
}

// ── Navigation destinations ───────────────────────────────────────────────────
private sealed class NavDest(val route: String, val icon: String, val label: String) {
    object Home    : NavDest("quickpics", "🏠", "Home")
    object Artists : NavDest("artists",   "🎤", "Artists")
    object Library : NavDest("library",   "📚", "Library")
    object Explore : NavDest("explore",   "🔭", "Explore")
}

private val mainDestinations = listOf(NavDest.Home, NavDest.Artists, NavDest.Library, NavDest.Explore)
private val mainRoutes       = mainDestinations.map { it.route }

@Composable
fun DesktopApp() {
    val navController       = rememberNavController()
    val backStackEntry      by navController.currentBackStackEntryAsState()
    val currentRoute        = backStackEntry?.destination?.route ?: "quickpics"
    val controller          = remember { VlcjController() }
    val scope               = rememberCoroutineScope()

    var currentSong         by remember { mutableStateOf<Song?>(null) }
    var activeStreamUrl     by remember { mutableStateOf<String?>(null) }
    var playbackMessage     by remember { mutableStateOf<String?>(null) }
    var isResolving         by remember { mutableStateOf(false) }
    var selectedMood        by remember { mutableStateOf<Innertube.Mood.Item?>(null) }
    var selectedAlbumId     by remember { mutableStateOf<String?>(null) }
    var selectedPlaylistId  by remember { mutableStateOf<String?>(null) }
    var selectedArtistId    by remember { mutableStateOf<String?>(null) }
    var searchQuery         by remember { mutableStateOf("") }
    var activeSearchQuery   by remember { mutableStateOf("") }

    DisposableEffect(Unit) { onDispose { controller.dispose() } }

    LaunchedEffect(activeStreamUrl) {
        val url = activeStreamUrl ?: return@LaunchedEffect
        controller.load(url)
        controller.play()
    }

    fun playSong(song: Song) {
        scope.launch {
            currentSong     = song
            playbackMessage = null
            isResolving     = true
            activeStreamUrl = null
            val url = resolveDesktopPlaybackUrl(song.id)
            if (url != null) {
                activeStreamUrl = url
                playbackMessage = "Playing"
            } else {
                playbackMessage = "Couldn't resolve stream for ${song.title}"
            }
            isResolving = false
        }
    }

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RiColor.bg)
        ) {
            // Ambient glow blobs
            Box(
                modifier = Modifier
                    .size(600.dp)
                    .offset((-100).dp, (-150).dp)
                    .blur(200.dp)
                    .background(
                        Brush.radialGradient(listOf(Color(0x1A0ABFBC), Color.Transparent)),
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(400.dp)
                    .align(Alignment.BottomEnd)
                    .offset(80.dp, 60.dp)
                    .blur(180.dp)
                    .background(
                        Brush.radialGradient(listOf(Color(0x151DB954), Color.Transparent)),
                        CircleShape
                    )
            )

            // Main layout
            Row(modifier = Modifier.fillMaxSize()) {

                // ── Icon Sidebar ─────────────────────────────────────────────
                GlassySidebar(
                    currentRoute = currentRoute,
                    onNavigate   = { route ->
                        navController.navigate(route) {
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                )

                // ── Content + Bottom player ──────────────────────────────────
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {

                    // Top header
                    GlassyTopBar(
                        currentRoute = currentRoute,
                        canGoBack    = currentRoute !in mainRoutes,
                        onBack       = { navController.popBackStack() },
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onSearchSubmit = {
                            val normalizedQuery = searchQuery.trim()
                            if (normalizedQuery.isNotBlank()) {
                                activeSearchQuery = normalizedQuery
                                navController.navigate("search")
                            }
                        }
                    )

                    // Nav host
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(RiColor.surfaceGlass)
                    ) {
                        NavHost(
                            navController    = navController,
                            startDestination = "quickpics",
                            modifier         = Modifier.fillMaxSize()
                        ) {
                            composable("quickpics") {
                                QuickPicsScreen(
                                    onSongClick     = ::playSong,
                                    onAlbumClick    = { browseId ->
                                        selectedAlbumId = browseId
                                        navController.navigate("album")
                                    },
                                    onArtistClick   = { browseId ->
                                        selectedArtistId = browseId
                                        navController.navigate("artist")
                                    },
                                    onPlaylistClick = { browseId ->
                                        selectedPlaylistId = browseId
                                        navController.navigate("playlist")
                                    },
                                    onMoodClick     = { mood ->
                                        selectedMood = mood
                                        navController.navigate("mood")
                                    }
                                )
                            }
                            composable("artists") {
                                ArtistsScreen(modifier = Modifier.fillMaxSize().padding(8.dp))
                            }
                            composable("artist") {
                                DesktopArtistRoute(
                                    browseId = selectedArtistId.orEmpty(),
                                    onSongClick = ::playSong,
                                    onPlaylistClick = { browseId: String ->
                                        selectedPlaylistId = browseId
                                        navController.navigate("playlist")
                                    },
                                    onAlbumClick = { browseId: String ->
                                        selectedAlbumId = browseId
                                        navController.navigate("album")
                                    }
                                )
                            }
                            composable("library") {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Library coming soon", color = RiColor.textSecond)
                                }
                            }
                            composable("explore") {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Explore coming soon", color = RiColor.textSecond)
                                }
                            }
                            composable("search") {
                                SearchScreen(
                                    query = activeSearchQuery,
                                    onSongClick = ::playSong,
                                    onAlbumClick = { browseId ->
                                        selectedAlbumId = browseId
                                        navController.navigate("album")
                                    },
                                    onArtistClick = { browseId ->
                                        selectedArtistId = browseId
                                        navController.navigate("artist")
                                    },
                                    onPlaylistClick = { browseId ->
                                        selectedPlaylistId = browseId
                                        navController.navigate("playlist")
                                    }
                                )
                            }
                            composable("album") {
                                AlbumScreen(
                                    browseId    = selectedAlbumId.orEmpty(),
                                    onSongClick = ::playSong,
                                    onAlbumClick = { nestedId ->
                                        selectedAlbumId = nestedId
                                        navController.navigate("album")
                                    }
                                )
                            }
                            composable("playlist") {
                                PlaylistScreen(
                                    browseId    = selectedPlaylistId.orEmpty(),
                                    onSongClick = ::playSong,
                                    onAlbumClick = { albumId ->
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
                                        mood          = mood,
                                        onAlbumClick  = { albumId ->
                                            selectedAlbumId = albumId
                                            navController.navigate("album")
                                        },
                                    onArtistClick = { artistId ->
                                        selectedArtistId = artistId
                                        navController.navigate("artist")
                                    },
                                        onPlaylistClick = { playlistId ->
                                            selectedPlaylistId = playlistId
                                            navController.navigate("playlist")
                                        }
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No mood selected", color = RiColor.textSecond)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── Bottom player rail ───────────────────────────────────
                    AnimatedVisibility(
                        visible = true,
                        enter   = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    ) {
                        GlassyPlayerRail(
                            controller       = controller,
                            currentSong      = currentSong,
                            isResolving      = isResolving,
                            playbackMessage  = playbackMessage,
                            modifier         = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Sidebar ───────────────────────────────────────────────────────────────────
@Composable
private fun GlassySidebar(currentRoute: String, onNavigate: (String) -> Unit) {
    Box(
        modifier = Modifier
            .width(72.dp)
            .fillMaxHeight()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xAA111922), Color(0xBB0B1520))
                )
            )
            .drawBehind {
                drawLine(
                    color  = Color(0xFF1E2D3D),
                    start  = Offset(size.width, 0f),
                    end    = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        Column(
            modifier            = Modifier.fillMaxSize().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Logo pill
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        Brush.radialGradient(listOf(RiColor.accent, Color(0xFF0A7A3A))),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("♪", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(32.dp))

            mainDestinations.forEach { dest ->
                SidebarIcon(
                    icon      = dest.icon,
                    label     = dest.label,
                    isActive  = currentRoute == dest.route || (dest.route == "quickpics" && currentRoute == "quickpics"),
                    onClick   = { onNavigate(dest.route) }
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.weight(1f))

            SidebarIcon(icon = "⚙", label = "Settings", isActive = false, onClick = {})
            Spacer(Modifier.height(12.dp))
            SidebarIcon(icon = "👤", label = "Account",  isActive = false, onClick = {})
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SidebarIcon(icon: String, label: String, isActive: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val bgAlpha by animateFloatAsState(
        targetValue = when { isActive -> 1f; isHovered -> 0.5f; else -> 0f },
        animationSpec = tween(150)
    )

    Column(
        modifier            = Modifier
            .width(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(RiColor.accentDim.copy(alpha = bgAlpha))
            .hoverable(interactionSource)
            .clickable(interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(Modifier.height(3.dp))
        Text(
            text      = label,
            fontSize  = 9.sp,
            color     = if (isActive) RiColor.accent else RiColor.textDim,
            maxLines  = 1,
            overflow  = TextOverflow.Clip
        )
    }
}

// ── Top Bar ───────────────────────────────────────────────────────────────────
@Composable
private fun GlassyTopBar(
    currentRoute: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit
) {
    Row(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (canGoBack) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(RiColor.glass)
                        .clickable(onClick = onBack)
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text("← Back", color = RiColor.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Column {
                Text(
                    text = when {
                        java.time.LocalTime.now().hour < 12 -> "Good morning 🎵"
                        java.time.LocalTime.now().hour < 17 -> "Good afternoon 🎵"
                        else                                -> "Good evening 🎵"
                    },
                    color     = RiColor.textPrimary,
                    fontSize  = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text     = when (currentRoute) {
                        "album"    -> "Album"
                        "playlist" -> "Playlist"
                        "mood"     -> "Mood"
                        "artists"  -> "Artists"
                        "library"  -> "Library"
                        "explore"  -> "Explore"
                        else       -> "RiMusic Desktop"
                    },
                    color    = RiColor.textSecond,
                    fontSize = 12.sp
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .width(280.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(RiColor.glass),
            singleLine = true,
            textStyle = TextStyle(color = RiColor.textPrimary, fontSize = 13.sp),
            placeholder = { Text("Search music...", color = RiColor.textDim, fontSize = 13.sp) },
            leadingIcon = { Text("🔍", fontSize = 14.sp) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    Text(
                        text = "Go",
                        color = RiColor.accent,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable(onClick = onSearchSubmit)
                    )
                }
            },
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = RiColor.glass,
                unfocusedContainerColor = RiColor.glass,
                focusedBorderColor = RiColor.accent.copy(alpha = 0.65f),
                unfocusedBorderColor = RiColor.border,
                cursorColor = RiColor.accent,
                focusedTextColor = RiColor.textPrimary,
                unfocusedTextColor = RiColor.textPrimary
            )
        )
    }
}

// ── Player Rail ───────────────────────────────────────────────────────────────
@Composable
private fun GlassyPlayerRail(
    controller      : VlcjController,
    currentSong     : Song?,
    isResolving     : Boolean,
    playbackMessage : String?,
    modifier        : Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xCC111C28), Color(0xCC0D1920), Color(0xCC111C28))
                )
            )
            .drawBehind {
                drawRoundRect(
                    color        = RiColor.border,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                    style        = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
                )
            }
    ) {
        // Subtle inner glow when playing
        if (currentSong != null && !isResolving) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, RiColor.accent.copy(0.6f), Color.Transparent)
                        )
                    )
            )
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Album art placeholder / music icon
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(RiColor.accentDim, Color(0xFF0A3329))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isResolving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color    = RiColor.accent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text     = if (currentSong != null) "▶" else "♫",
                            color    = if (currentSong != null) RiColor.accent else RiColor.textDim,
                            fontSize = 18.sp
                        )
                    }
                }

                // Track info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text       = currentSong?.title ?: "Select a track to start playing",
                        color      = if (currentSong != null) RiColor.textPrimary else RiColor.textSecond,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Text(
                        text     = when {
                            isResolving                       -> "Resolving stream…"
                            !playbackMessage.isNullOrBlank() && playbackMessage != "Playing"
                                                              -> playbackMessage!!
                            !currentSong?.artistsText.isNullOrBlank()
                                                              -> currentSong!!.artistsText!!
                            else                              -> "RiMusic"
                        },
                        color    = if (!playbackMessage.isNullOrBlank() && playbackMessage != "Playing")
                                        RiColor.red
                                   else RiColor.textSecond,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Playback controls from DefaultBottomBar
            DefaultBottomBar(controller = controller)
        }
    }
}

// ── Resolve stream URL ────────────────────────────────────────────────────────
private suspend fun resolveDesktopPlaybackUrl(videoId: String): String? = withContext(Dispatchers.IO) {
    val primary = runCatching {
        Innertube.player(videoId = videoId)
            ?.getOrNull()
            ?.streamingData
            ?.autoMaxQualityFormat
            ?.let { f ->
                val range = if (f.contentLength != null) "0-${f.contentLength}" else "0-10000000"
                "${f.url}&range=$range"
            }
    }.getOrNull()
    if (!primary.isNullOrBlank()) return@withContext primary

    runCatching {
        Invidious.api.videos(videoId)
            ?.getOrNull()
            ?.adaptiveFormats
            .orEmpty()
            .asSequence()
            .mapNotNull { fmt ->
                val url      = readProp<String>(fmt, "url")
                val mime     = readProp<String>(fmt, "type") ?: readProp<String>(fmt, "mimeType")
                val bitrate  = readProp<Int>(fmt, "bitrate") ?: 0
                if (url.isNullOrBlank() || mime?.contains("audio", ignoreCase = true) != true) null
                else bitrate to url
            }
            .maxByOrNull { it.first }
            ?.second
    }.getOrNull()
}

private inline fun <reified T> readProp(instance: Any, name: String): T? = runCatching {
    val getter = instance.javaClass.methods.firstOrNull { m ->
        m.parameterCount == 0 &&
        (m.name == "get${name.replaceFirstChar { it.uppercase() }}" || m.name == name)
    }
    (getter?.invoke(instance) ?: instance.javaClass.fields.firstOrNull { it.name == name }?.get(instance)) as? T
}.getOrNull()
