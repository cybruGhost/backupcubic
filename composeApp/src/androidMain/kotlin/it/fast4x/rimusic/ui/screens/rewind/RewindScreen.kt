package it.fast4x.rimusic.ui.screens.rewind

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.time.LocalDate

// Remove the import that was causing the error: "import coil.compose.AsyncImage"
// Instead, import the Coil Compose library properly
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import java.net.URL

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RewindScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit = {},
) {
    var rewindData by remember { mutableStateOf<RewindData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })
    
    // State for storing fetched thumbnails
    var songThumbnails by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var artistThumbnails by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    
    // Fetch data when screen loads
    LaunchedEffect(Unit) {
        scope.launch {
            rewindData = RewindDataFetcher.getRewindData()
            
            // Fetch thumbnails for top songs and artists
            rewindData?.let { data ->
                val songThumbs = mutableMapOf<String, String>()
                val artistThumbs = mutableMapOf<String, String>()
                
                // Fetch thumbnails for top songs
                data.topSongs.forEach { song ->
                    val thumbnailUrl = fetchSongThumbnail(song.title, song.artist)
                    thumbnailUrl?.let {
                        songThumbs[song.title + song.artist] = it
                    }
                }
                
                // Fetch thumbnails for top artists
                data.topArtists.forEach { artist ->
                    val thumbnailUrl = fetchArtistThumbnail(artist.name)
                    thumbnailUrl?.let {
                        artistThumbs[artist.name] = it
                    }
                }
                
                songThumbnails = songThumbs
                artistThumbnails = artistThumbs
            }
            
            isLoading = false
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Gradient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F0C29),
                            Color(0xFF302B63),
                            Color(0xFF24243E)
                        )
                    )
                )
        )
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Loading your musical journey...",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> WelcomeScreen(
                        onGetStarted = {
                            scope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        }
                    )
                    1 -> RewindContentScreen(
                        rewindData = rewindData,
                        songThumbnails = songThumbnails,
                        artistThumbnails = artistThumbnails
                    )
                }
            }
            
            // Page indicators
            if (!isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 100.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    PageIndicator(
                        pageCount = 2,
                        currentPage = pagerState.currentPage,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
    
    miniPlayer()
}

// Function to fetch song thumbnail from API
suspend fun fetchSongThumbnail(songTitle: String, artist: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val query = URLEncoder.encode("$songTitle $artist", "UTF-8")
            val url = "https://yt.omada.cafe/api/v1/search?q=$query&type=video"
            
            val connection = java.net.URL(url).openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val response = connection.getInputStream().bufferedReader().use { it.readText() }
            
            // Parse JSON response to find video thumbnail
            if (response.contains("\"videoThumbnails\"")) {
                val thumbnailStart = response.indexOf("\"videoThumbnails\"")
                val urlStart = response.indexOf("\"url\":\"", thumbnailStart) + 7
                val urlEnd = response.indexOf("\"", urlStart)
                val thumbnailUrl = response.substring(urlStart, urlEnd).replace("\\/", "/")
                
                // Prepend https: if not present
                return@withContext if (thumbnailUrl.startsWith("//")) {
                    "https:$thumbnailUrl"
                } else if (!thumbnailUrl.startsWith("http")) {
                    "https://$thumbnailUrl"
                } else {
                    thumbnailUrl
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}

// Function to fetch artist thumbnail from API
suspend fun fetchArtistThumbnail(artistName: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val query = URLEncoder.encode(artistName, "UTF-8")
            val url = "https://yt.omada.cafe/api/v1/search?q=$query&type=channel"
            
            val connection = java.net.URL(url).openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val response = connection.getInputStream().bufferedReader().use { it.readText() }
            
            // Parse JSON response to find author thumbnail
            if (response.contains("\"authorThumbnails\"")) {
                val thumbnailsStart = response.indexOf("\"authorThumbnails\"")
                // Try to get a medium-sized thumbnail
                val urlStart = response.indexOf("\"url\":\"", thumbnailsStart) + 7
                val urlEnd = response.indexOf("\"", urlStart)
                val thumbnailUrl = response.substring(urlStart, urlEnd).replace("\\/", "/")
                
                // Prepend https: if not present
                return@withContext if (thumbnailUrl.startsWith("//")) {
                    "https:$thumbnailUrl"
                } else if (!thumbnailUrl.startsWith("http")) {
                    "https://$thumbnailUrl"
                } else {
                    thumbnailUrl
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}

// Simple image loader composable without Coil
@Composable
fun NetworkImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = Color.White
            )
        }
    }
) {
    var imageBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(url) {
        if (url != null) {
            isLoading = true
            imageBitmap = withContext(Dispatchers.IO) {
                try {
                    val connection = URL(url).openConnection()
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    val inputStream = connection.getInputStream()
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    bitmap?.asImageBitmap()
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            isLoading = false
        }
    }
    
    if (isLoading) {
        Box(modifier = modifier) {
            placeholder()
        }
    } else if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap!!,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        // Fallback if image fails to load
        Box(
            modifier = modifier.background(Color(0x1AFFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Text("🎵", fontSize = 20.sp)
        }
    }
}

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated music icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF4081),
                            Color(0xFF9C27B0)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🎵",
                fontSize = 48.sp
            )
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // Title
        Text(
            text = "Rewind",
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Your year in music",
            fontSize = 18.sp,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Description
        Text(
            text = "Your listening, your discoveries and your obsessions. " +
                   "Get ready to relive your most unforgettable musical moments.",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 16.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Privacy message
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🔒",
                    fontSize = 20.sp
                )
                Text(
                    text = "Your privacy is respected. All data is stored only on your device.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // Get Started button
        Button(
            onClick = onGetStarted,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF4081)
            ),
            shape = RoundedCornerShape(25.dp),
            modifier = Modifier
                .height(50.dp)
                .fillMaxWidth(0.7f)
        ) {
            Text(
                text = "Get Started",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Swipe hint
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "→",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = "Swipe to continue",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun RewindContentScreen(
    rewindData: RewindData?,
    songThumbnails: Map<String, String> = emptyMap(),
    artistThumbnails: Map<String, String> = emptyMap()
) {
    val data = rewindData ?: return
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header with year
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Your ${LocalDate.now().year} Rewind",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "A year of unforgettable music",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
        
        // Main Stats Cards - FIXED: Pass the entire data object
        item {
            StatsCards(data)
        }
        
        // Top Songs
        item {
            if (data.topSongs.isNotEmpty()) {
                TopSongsSection(
                    songs = data.topSongs,
                    thumbnails = songThumbnails
                )
            }
        }
        
        // Top Artists
        item {
            if (data.topArtists.isNotEmpty()) {
                TopArtistsSection(
                    artists = data.topArtists,
                    thumbnails = artistThumbnails
                )
            }
        }
        
        // Extra Stats
        item {
            ExtraStatsSection(data)
        }
        
        // Share button
        item {
            Button(
                onClick = { /* Share your rewind */ },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0x1AFFFFFF)
                ),
                shape = RoundedCornerShape(25.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📤", fontSize = 18.sp)
                    Text(
                        text = "Share Your Rewind",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(pageCount) { page ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        color = if (page == currentPage) 
                            Color.White 
                        else 
                            Color.White.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

// FIXED: Changed parameter from ListeningStats to RewindData
@Composable
fun StatsCards(data: RewindData) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Main stats card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x15FFFFFF))
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "🎧 Listening Summary",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem(
                        value = data.stats.totalPlays.toString(),
                        label = "Total Plays",
                        emoji = "▶️"
                    )
                    StatItem(
                        value = String.format("%.1f", data.stats.listeningHours),
                        label = "Hours Listened",
                        emoji = "⏱️"
                    )
                    // FIXED: Access totalUniqueSongs from data, not stats
                    StatItem(
                        value = data.totalUniqueSongs.toString(),
                        label = "Unique Songs",
                        emoji = "🎶"
                    )
                }
            }
        }
        
        // Active times card
        if (data.stats.mostActiveDay != null || data.stats.mostActiveHour != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x15FFFFFF))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "⏰ Active Listening Times",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (data.stats.mostActiveDay != null) {
                            StatItem(
                                value = data.stats.mostActiveDay,
                                label = "Most Active Day",
                                emoji = "📅"
                            )
                        }
                        if (data.stats.mostActiveHour != null) {
                            StatItem(
                                value = data.stats.mostActiveHour,
                                label = "Most Active Hour",
                                emoji = "🕒"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(
    value: String,
    label: String,
    emoji: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = emoji,
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun TopSongsSection(
    songs: List<TopSong>,
    thumbnails: Map<String, String> = emptyMap()
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x15FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔥 Top Songs",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = LocalDate.now().year.toString(),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            songs.forEachIndexed { index, song ->
                val thumbnailKey = song.title + song.artist
                val thumbnailUrl = thumbnails[thumbnailKey]
                
                TopSongItem(
                    rank = index + 1,
                    song = song,
                    thumbnailUrl = thumbnailUrl,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun TopSongItem(
    rank: Int,
    song: TopSong,
    thumbnailUrl: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x0AFFFFFF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Rank badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        when (rank) {
                            1 -> Color(0xFFFFD700)
                            2 -> Color(0xFFC0C0C0)
                            3 -> Color(0xFFCD7F32)
                            else -> Color(0xFF9C27B0)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$rank",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            
            // Song thumbnail using our custom NetworkImage
            NetworkImage(
                url = thumbnailUrl,
                contentDescription = "${song.title} thumbnail",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                placeholder = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x1AFFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎵", fontSize = 20.sp)
                    }
                }
            )
            
            // Song info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Play count with badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x1AFFFFFF))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${song.playCount} plays",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun TopArtistsSection(
    artists: List<TopArtist>,
    thumbnails: Map<String, String> = emptyMap()
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x15FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "⭐ Top Artists",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            artists.forEachIndexed { index, artist ->
                val thumbnailUrl = thumbnails[artist.name]
                
                TopArtistItem(
                    rank = index + 1,
                    artist = artist,
                    thumbnailUrl = thumbnailUrl,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun TopArtistItem(
    rank: Int,
    artist: TopArtist,
    thumbnailUrl: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x0AFFFFFF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Artist avatar with thumbnail using our custom NetworkImage
            NetworkImage(
                url = thumbnailUrl,
                contentDescription = "${artist.name} thumbnail",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                placeholder = {
                    // Fallback gradient avatar
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        when (rank % 4) {
                                            0 -> Color(0xFFE91E63)
                                            1 -> Color(0xFF2196F3)
                                            2 -> Color(0xFF4CAF50)
                                            else -> Color(0xFFFF9800)
                                        },
                                        Color(0xFF9C27B0)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = artist.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                }
            )
            
            // Artist info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = artist.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "#${rank} Artist",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            
            // Play count
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x1AFFFFFF))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${artist.playCount} plays",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ExtraStatsSection(data: RewindData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x15FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "📊 More Insights",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Stats grid
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InsightCard(
                        title = "First Play",
                        value = data.stats.firstPlayDate ?: "—",
                        emoji = "🎬",
                        color = Color(0xFF9C27B0),
                        modifier = Modifier.weight(1f)
                    )
                    
                    InsightCard(
                        title = "Last Play",
                        value = data.stats.lastPlayDate ?: "—",
                        emoji = "⏹️",
                        color = Color(0xFF2196F3),
                        modifier = Modifier.weight(1f)
                    )
                }
                
                if (data.favoriteGenre != null) {
                    InsightCard(
                        title = "Favorite Genre",
                        value = data.favoriteGenre,
                        emoji = "🎵",
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun InsightCard(
    title: String,
    value: String,
    emoji: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = emoji,
                fontSize = 24.sp
            )
            
            Column {
                Text(
                    text = value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}