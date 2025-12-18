package it.fast4x.rimusic.ui.screens.rewind

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import it.fast4x.rimusic.utils.DataStoreUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import android.content.Intent
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.runBlocking
import androidx.compose.foundation.BorderStroke

// Import the data classes and fetcher from your original code
import it.fast4x.rimusic.ui.screens.rewind.RewindDataFetcher
import it.fast4x.rimusic.ui.screens.rewind.RewindData
import it.fast4x.rimusic.ui.screens.rewind.ListeningStats
import it.fast4x.rimusic.ui.screens.rewind.TopSong
import it.fast4x.rimusic.ui.screens.rewind.TopArtist

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RewindScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit = {},
) {
    var rewindData by remember { mutableStateOf<RewindData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("Music Fan") }
    var userRanking by remember { mutableStateOf("Explorer") }
    var userPercentile by remember { mutableStateOf("Top 50%") }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                username = runBlocking {
                    DataStoreUtils.getStringBlocking(context, DataStoreUtils.KEY_USERNAME, "Music Fan")
                }
                
                // Calculate ranking based on join date and stats
                val joinDate = runBlocking {
                    DataStoreUtils.getStringBlocking(context, "join_date", "")
                }
                rewindData = RewindDataFetcher.getRewindData()
                
                // Calculate ranking
                val rankingResult = calculateUserRanking(rewindData, joinDate)
                userRanking = rankingResult.first
                userPercentile = rankingResult.second
                
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F0B1A),
                            Color(0xFF1A1529),
                            Color(0xFF2A1F3A)
                        )
                    )
                )
        )
        
        if (isLoading) {
            LoadingScreen()
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> WelcomeScreen(
                        username = username,
                        onGetStarted = {
                            scope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        }
                    )
                    1 -> RewindContentScreen(
                        username = username,
                        rewindData = rewindData,
                        userRanking = userRanking,
                        userPercentile = userPercentile,
                        onOpenDetailedRewind = {
                            val dataString = encodeRewindDataForUrl(rewindData, username)
                            val url = "https://cubicrewind.lovable.app?data=$dataString"
                            uriHandler.openUri(url)
                        },
                        onShareRewind = {
                            shareRewindData(context, rewindData, username, userRanking)
                        }
                    )
                }
            }
            
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

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF9C27B0),
                                Color(0xFF673AB7)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎵",
                    fontSize = 36.sp
                )
            }
            
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(48.dp)
            )
            
            Text(
                text = "Generating your musical journey...",
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun WelcomeScreen(
    username: String,
    onGetStarted: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Logo/Icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF9C27B0),
                            Color(0xFF673AB7)
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
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Welcome Title
        Text(
            text = "REWIND ${LocalDate.now().year}",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Welcome message
        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(color = Color.White.copy(alpha = 0.8f))) {
                    append("Welcome back, ")
                }
                withStyle(style = SpanStyle(
                    color = Color(0xFFE040FB),
                    fontWeight = FontWeight.Bold
                )) {
                    append(username)
                }
                withStyle(style = SpanStyle(color = Color.White.copy(alpha = 0.8f))) {
                    append("!")
                }
            },
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Description
        Text(
            text = "Your personal music journey for ${LocalDate.now().year}",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 16.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Feature highlights
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            FeatureItem(icon = "📊", text = "Detailed listening statistics")
            FeatureItem(icon = "🔥", text = "Top songs & artists")
            FeatureItem(icon = "⭐", text = "Personal ranking")
            FeatureItem(icon = "🔒", text = "100% private & secure")
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // Privacy note
        Text(
            text = "Your privacy is respected.\nAll data is stored only on your device.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Get Started button
        Button(
            onClick = onGetStarted,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF9C27B0)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            contentPadding = PaddingValues(horizontal = 24.dp)
        ) {
            Text(
                text = "Get Started",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun FeatureItem(icon: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = icon,
            fontSize = 20.sp,
            color = Color(0xFFE040FB)
        )
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 15.sp
        )
    }
}

@Composable
fun RewindContentScreen(
    username: String,
    rewindData: RewindData?,
    userRanking: String,
    userPercentile: String,
    onOpenDetailedRewind: () -> Unit,
    onShareRewind: () -> Unit
) {
    val data = rewindData ?: return
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎵 REWIND ${LocalDate.now().year}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    buildAnnotatedString {
                        withStyle(style = SpanStyle(color = Color.White.copy(alpha = 0.8f))) {
                            append("For ")
                        }
                        withStyle(style = SpanStyle(
                            color = Color(0xFFE040FB),
                            fontWeight = FontWeight.Bold
                        )) {
                            append(username)
                        }
                    },
                    fontSize = 18.sp,
                    color = Color.White
                )
            }
        }
        
        // User Ranking Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2A1F3A)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Your Ranking",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = userRanking,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = userPercentile,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFFFD700),
                                        Color(0xFFFFC107)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🏆",
                            fontSize = 28.sp
                        )
                    }
                }
            }
        }
        
        // Action Buttons
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenDetailedRewind,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFE040FB)
                    ),
                    border = BorderStroke(1.5.dp, Color(0xFFE040FB))
                ) {
                    Text(
                        text = "🌐 Detailed Rewind",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Button(
                    onClick = onShareRewind,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9C27B0)
                    )
                ) {
                    Text(
                        text = "📤 Share",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        // Main Stats Card
        item {
            MainStatsCard(data)
        }
        
        // Top Songs Section
        item {
            if (data.topSongs.isNotEmpty()) {
                TopSongsCard(data.topSongs)
            }
        }
        
        // Top Artists Section
        item {
            if (data.topArtists.isNotEmpty()) {
                TopArtistsCard(data.topArtists)
            }
        }
        
        // Listening Patterns with Last Played
        item {
            ListeningPatternsCard(data.stats)
        }
        
        // More Insights
        item {
            MoreInsightsCard(data)
        }
        
        // Footer note
        item {
            Text(
                text = "Your data is stored locally on your device only",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 24.dp)
            )
        }
    }
}

@Composable
fun MainStatsCard(data: RewindData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A1F3A)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "📊 Your Year in Numbers",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatBox(
                    value = data.stats.totalPlays.toString(),
                    label = "Total Plays",
                    icon = "▶️",
                    color = Color(0xFFE040FB),
                    modifier = Modifier.weight(1f)
                )
                
                StatBox(
                    value = String.format("%.1f", data.stats.listeningHours),
                    label = "Hours Listened",
                    icon = "⏱️",
                    color = Color(0xFF7E57C2),
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatBox(
                    value = data.totalUniqueSongs.toString(),
                    label = "Unique Songs",
                    icon = "🎶",
                    color = Color(0xFFAB47BC),
                    modifier = Modifier.weight(1f)
                )
                
                StatBox(
                    value = data.topArtists.size.toString(),
                    label = "Top Artists",
                    icon = "⭐",
                    color = Color(0xFFCE93D8),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun StatBox(
    value: String,
    label: String,
    icon: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = icon,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TopSongsCard(songs: List<TopSong>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A1F3A)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "🔥 Top Songs",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            songs.take(10).forEachIndexed { index, song ->
                TopSongItem(
                    rank = index + 1,
                    song = song,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun TopSongItem(
    rank: Int,
    song: TopSong,
    modifier: Modifier = Modifier
) {
    var thumbnailUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(song.title, song.artist) {
        if (song.title.isNotEmpty() && song.artist.isNotEmpty()) {
            scope.launch {
                thumbnailUrl = fetchSongThumbnail(song.title, song.artist)
            }
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF3D2E54)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rank badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        when (rank) {
                            1 -> Color(0xFFFFD700)
                            2 -> Color(0xFFC0C0C0)
                            3 -> Color(0xFFCD7F32)
                            else -> Color(0xFF9C27B0).copy(alpha = 0.5f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$rank",
                    color = if (rank <= 3) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            
            // Song thumbnail with fallback
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                NetworkImage(
                    url = thumbnailUrl,
                    contentDescription = "Song thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            
            // Song info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
                Text(
                    text = song.artist,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Play count
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF9C27B0).copy(alpha = 0.3f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${song.playCount}",
                    fontSize = 14.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun TopArtistsCard(artists: List<TopArtist>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A1F3A)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "⭐ Top Artists",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val chunkedArtists = artists.take(9).chunked(3)
            
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                chunkedArtists.forEach { rowArtists ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowArtists.forEach { artist ->
                            ArtistCard(artist = artist, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistCard(
    artist: TopArtist,
    modifier: Modifier = Modifier
) {
    var thumbnailUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(artist.name) {
        if (artist.name.isNotEmpty()) {
            scope.launch {
                thumbnailUrl = fetchArtistThumbnail(artist.name)
            }
        }
    }
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF3D2E54)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Artist thumbnail with fallback
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                NetworkImage(
                    url = thumbnailUrl,
                    contentDescription = "Artist thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = artist.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
            
            Text(
                text = "${artist.playCount} plays",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ListeningPatternsCard(stats: ListeningStats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A1F3A)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "⏰ Listening Patterns",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // First row: Most Active Day and Hour
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (stats.mostActiveDay != null) {
                    PatternItem(
                        icon = "📅",
                        title = "Most Active Day",
                        value = stats.mostActiveDay,
                        color = Color(0xFF7E57C2),
                        modifier = Modifier.weight(1f)
                    )
                }
                
                if (stats.mostActiveHour != null) {
                    PatternItem(
                        icon = "🕒",
                        title = "Most Active Hour",
                        value = stats.mostActiveHour,
                        color = Color(0xFFAB47BC),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Second row: First Play and Last Played
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (stats.firstPlayDate != null) {
                    PatternItem(
                        icon = "🎬",
                        title = "First Play",
                        value = stats.firstPlayDate,
                        color = Color(0xFFCE93D8),
                        modifier = Modifier.weight(1f)
                    )
                }
                
                if (stats.lastPlayDate != null) {
                    PatternItem(
                        icon = "⏹️",
                        title = "Last Played",
                        value = stats.lastPlayDate,
                        color = Color(0xFFE040FB),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun PatternItem(
    icon: String,
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = icon,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = title,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MoreInsightsCard(data: RewindData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A1F3A)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "📈 More Insights",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Average plays per day
            val avgPlaysPerDay = if (data.stats.totalPlays > 0) {
                String.format("%.1f", data.stats.totalPlays / 365.0)
            } else "0"
            
            InsightRow(
                icon = "📊",
                title = "Daily Average",
                value = "$avgPlaysPerDay plays/day"
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (data.favoriteGenre != null) {
                InsightRow(
                    icon = "🎵",
                    title = "Favorite Genre",
                    value = data.favoriteGenre
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Add average listening session time
            val avgSessionHours = if (data.stats.listeningHours > 0 && data.stats.totalPlays > 0) {
                val avgMinutes = (data.stats.listeningHours * 60) / data.stats.totalPlays
                String.format("%.1f", avgMinutes)
            } else "0"
            
            InsightRow(
                icon = "⏱️",
                title = "Avg. Session",
                value = "${avgSessionHours} min"
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Add peak month (simplified - you might want to calculate this properly)
            val currentMonth = LocalDate.now().month.getDisplayName(
                java.time.format.TextStyle.FULL, 
                java.util.Locale.getDefault()
            )
            
            InsightRow(
                icon = "📈",
                title = "Current Month",
                value = currentMonth
            )
        }
    }
}

@Composable
fun InsightRow(
    icon: String,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = icon,
            fontSize = 24.sp,
            color = Color(0xFFE040FB)
        )
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
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
                            Color(0xFFE040FB)
                        else 
                            Color.White.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

// Function to fetch song thumbnail from API
suspend fun fetchSongThumbnail(songTitle: String, artist: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val query = URLEncoder.encode("$songTitle $artist", "UTF-8")
            val url = "https://yt.omada.cafe/api/v1/search?q=$query&type=video"
            
            val connection = URL(url).openConnection()
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
            
            val connection = URL(url).openConnection()
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
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
) {
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
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
        } else {
            isLoading = false
        }
    }
    
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF3D2E54),
                        Color(0xFF2A1F3A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            placeholder()
        } else if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap!!,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            // Fallback emoji if image fails to load
            Text(
                text = if (contentDescription?.contains("artist") == true) "🎤" else "🎵",
                fontSize = 20.sp
            )
        }
    }
}

// Calculate user ranking based on join date and stats
private fun calculateUserRanking(data: RewindData?, joinDate: String): Pair<String, String> {
    if (data == null) return Pair("New Listener", "Just Starting")
    
    val totalHours = data.stats.listeningHours
    val totalPlays = data.stats.totalPlays
    val uniqueSongs = data.totalUniqueSongs
    
    // Calculate score based on multiple factors
    var score = 0
    
    // Hours listened scoring
    score += when {
        totalHours >= 1000 -> 30
        totalHours >= 500 -> 25
        totalHours >= 200 -> 20
        totalHours >= 100 -> 15
        totalHours >= 50 -> 10
        totalHours >= 20 -> 5
        else -> 1
    }
    
    // Total plays scoring
    score += when {
        totalPlays >= 10000 -> 30
        totalPlays >= 5000 -> 25
        totalPlays >= 2000 -> 20
        totalPlays >= 1000 -> 15
        totalPlays >= 500 -> 10
        totalPlays >= 100 -> 5
        else -> 1
    }
    
    // Unique songs scoring
    score += when {
        uniqueSongs >= 1000 -> 20
        uniqueSongs >= 500 -> 15
        uniqueSongs >= 200 -> 10
        uniqueSongs >= 100 -> 5
        else -> 1
    }
    
    // Join date consideration (simplified)
    if (joinDate.isNotEmpty()) {
        try {
            val joinYear = joinDate.substring(0, 4).toInt()
            val currentYear = LocalDate.now().year
            val yearsActive = currentYear - joinYear
            
            score += when {
                yearsActive >= 5 -> 20
                yearsActive >= 3 -> 15
                yearsActive >= 2 -> 10
                yearsActive >= 1 -> 5
                else -> 1
            }
        } catch (e: Exception) {
            // If join date parsing fails, add base score
            score += 1
        }
    }
    
    // Determine ranking and percentile
    return when {
        score >= 80 -> Pair("Legendary Listener", "Top 1%")
        score >= 60 -> Pair("Music Enthusiast", "Top 10%")
        score >= 40 -> Pair("Regular Listener", "Top 25%")
        score >= 20 -> Pair("Casual Listener", "Top 50%")
        else -> Pair("New Explorer", "Top 75%")
    }
}

private fun encodeRewindDataForUrl(data: RewindData?, username: String): String {
    if (data == null) return ""
    
    return try {
        val jsonString = buildString {
            append("{")
            append("\"username\":\"${escapeJsonString(username)}\",")
            append("\"year\":\"${LocalDate.now().year}\",")
            append("\"totalPlays\":${data.stats.totalPlays},")
            append("\"listeningHours\":${data.stats.listeningHours},")
            append("\"uniqueSongs\":${data.totalUniqueSongs},")
            
            append("\"topSongs\":[")
            data.topSongs.take(5).forEachIndexed { index, song ->
                if (index > 0) append(",")
                append("{")
                append("\"title\":\"${escapeJsonString(song.title)}\",")
                append("\"artist\":\"${escapeJsonString(song.artist)}\",")
                append("\"plays\":${song.playCount}")
                append("}")
            }
            append("],")
            
            append("\"topArtists\":[")
            data.topArtists.take(5).forEachIndexed { index, artist ->
                if (index > 0) append(",")
                append("{")
                append("\"name\":\"${escapeJsonString(artist.name)}\",")
                append("\"plays\":${artist.playCount}")
                append("}")
            }
            append("],")
            
            append("\"listeningPatterns\":{")
            data.stats.mostActiveDay?.let {
                append("\"mostActiveDay\":\"${escapeJsonString(it)}\",")
            }
            data.stats.mostActiveHour?.let {
                append("\"mostActiveHour\":\"${escapeJsonString(it)}\",")
            }
            data.stats.firstPlayDate?.let {
                append("\"firstPlay\":\"${escapeJsonString(it)}\",")
            }
            data.stats.lastPlayDate?.let {
                append("\"lastPlay\":\"${escapeJsonString(it)}\",")
            }
            if (toString().endsWith(",")) {
                deleteCharAt(length - 1)
            }
            append("},")
            
            data.favoriteGenre?.let {
                append("\"favoriteGenre\":\"${escapeJsonString(it)}\"")
            } ?: append("\"favoriteGenre\":\"Various\"")
            
            append("}")
        }
        URLEncoder.encode(jsonString, "UTF-8")
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }
}

private fun escapeJsonString(str: String): String {
    return str
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}

private fun shareRewindData(context: Context, data: RewindData?, username: String, ranking: String) {
    if (data == null) return
    
    val shareText = buildString {
        append("🎵 My ${LocalDate.now().year} Music Rewind 🎵\n\n")
        append("Username: $username\n")
        append("Ranking: $ranking\n\n")
        append("📊 Stats:\n")
        append("• Total Plays: ${data.stats.totalPlays}\n")
        append("• Hours Listened: ${String.format("%.1f", data.stats.listeningHours)}\n")
        append("• Unique Songs: ${data.totalUniqueSongs}\n")
        
        if (data.stats.lastPlayDate != null) {
            append("• Last Played: ${data.stats.lastPlayDate}\n")
        }
        
        append("\n")
        
        if (data.topSongs.isNotEmpty()) {
            append("🔥 Top Songs:\n")
            data.topSongs.take(3).forEachIndexed { index, song ->
                append("${index + 1}. ${song.title} - ${song.artist}\n")
            }
            append("\n")
        }
        
        if (data.topArtists.isNotEmpty()) {
            append("⭐ Top Artists:\n")
            data.topArtists.take(3).forEachIndexed { index, artist ->
                append("${index + 1}. ${artist.name}\n")
            }
        }
        
        if (data.favoriteGenre != null) {
            append("\n🎵 Favorite Genre: ${data.favoriteGenre}\n")
        }
        
        append("\nGenerated with Cubic Rewind 🎵")
    }
    
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
        putExtra(Intent.EXTRA_SUBJECT, "My ${LocalDate.now().year} Music Rewind")
    }
    
    context.startActivity(Intent.createChooser(shareIntent, "Share Your Rewind"))
}