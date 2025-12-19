package it.fast4x.rimusic.ui.screens.rewind

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import android.content.Intent
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.runBlocking
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.clickable

// Color palettes for background gradients
val colorPalettes = listOf(
    listOf(Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF3F51B5)),  // Pink/Purple/Blue
    listOf(Color(0xFFE7D858), Color(0xFF733B81)),                      // Yellow/Purple
    listOf(Color(0xFF5A6CD2), Color(0xFF1DB954)),                      // Blue/Green
    listOf(Color(0xFF2196F3), Color(0xFF3F51B5)),                      // Blue/Indigo
    listOf(Color(0xFFFF9800), Color(0xFFFF5722)),                      // Orange/Deep Orange
    listOf(Color(0xFFE91E63), Color(0xFF9C27B0)),                      // Pink/Purple
    listOf(Color(0xFF1DB954), Color(0xFFBBA0A0))                       // Green/Gray
)

// Month names for display
val monthNames = mapOf(
    1 to "January", 2 to "February", 3 to "March", 4 to "April",
    5 to "May", 6 to "June", 7 to "July", 8 to "August",
    9 to "September", 10 to "October", 11 to "November", 12 to "December"
)

// Day names for display
val dayNames = mapOf(
    "MON" to "Monday", "TUE" to "Tuesday", "WED" to "Wednesday", 
    "THU" to "Thursday", "FRI" to "Friday", "SAT" to "Saturday", "SUN" to "Sunday"
)

// Hour labels with descriptions
val hourDescriptions = mapOf(
    "00:00" to "Midnight", "01:00" to "Early Night", "02:00" to "Late Night",
    "03:00" to "Late Night", "04:00" to "Early Morning", "05:00" to "Dawn",
    "06:00" to "Morning", "07:00" to "Morning", "08:00" to "Morning Commute",
    "09:00" to "Morning", "10:00" to "Mid-morning", "11:00" to "Late Morning",
    "12:00" to "Noon", "13:00" to "Early Afternoon", "14:00" to "Afternoon",
    "15:00" to "Mid-afternoon", "16:00" to "Late Afternoon", "17:00" to "Evening Start",
    "18:00" to "Evening", "19:00" to "Evening", "20:00" to "Night",
    "21:00" to "Late Evening", "22:00" to "Late Evening", "23:00" to "Late Night"
)

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
    var showMinutesInHours by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val currentYear = LocalDate.now().year
    
    // Main pager state for slides (10 slides total)
    val mainPagerState = rememberPagerState(pageCount = { 10 })
    
    // Loading optimization - load data in chunks
    var loadedTopSongs by remember { mutableStateOf<List<TopSong>?>(null) }
    var loadedTopArtists by remember { mutableStateOf<List<TopArtist>?>(null) }
    var loadedTopAlbums by remember { mutableStateOf<List<TopAlbum>?>(null) }
    var loadedTopPlaylists by remember { mutableStateOf<List<TopPlaylist>?>(null) }
    var loadedStats by remember { mutableStateOf<ListeningStats?>(null) }
    var loadedMonthlyStats by remember { mutableStateOf<List<MonthlyStat>?>(null) }
    var loadedDailyStats by remember { mutableStateOf<List<DailyStat>?>(null) }
    var loadedHourlyStats by remember { mutableStateOf<List<HourlyStat>?>(null) }
    
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                username = runBlocking {
                    DataStoreUtils.getStringBlocking(context, DataStoreUtils.KEY_USERNAME, "Music Fan")
                }
                
                // Load all data
                rewindData = RewindDataFetcher.getRewindData(currentYear)
                
                if (rewindData != null) {
                    loadedStats = rewindData!!.stats
                    loadedTopSongs = rewindData!!.topSongs
                    loadedTopArtists = rewindData!!.topArtists
                    loadedTopAlbums = rewindData!!.topAlbums
                    loadedTopPlaylists = rewindData!!.topPlaylists
                    loadedMonthlyStats = rewindData!!.monthlyStats
                    loadedDailyStats = rewindData!!.dailyStats
                    loadedHourlyStats = rewindData!!.hourlyStats
                    
                    // Calculate ranking
                    val joinDate = runBlocking {
                        DataStoreUtils.getStringBlocking(context, "join_date", "")
                    }
                    val rankingResult = calculateUserRanking(rewindData!!, joinDate)
                    userRanking = rankingResult.first
                    userPercentile = rankingResult.second
                }
                
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
        // Dynamic background based on slide
        val currentPalette = colorPalettes[mainPagerState.currentPage % colorPalettes.size]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = currentPalette
                    )
                )
        )
        
        if (isLoading) {
            LoadingScreen()
        } else {
            val data = rewindData ?: return
            
            HorizontalPager(
                state = mainPagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> WelcomeSlide(username, currentYear) {
                        scope.launch { mainPagerState.animateScrollToPage(1) }
                    }
                    1 -> StatsSlide(username, data, userRanking, userPercentile, showMinutesInHours) {
                        scope.launch { mainPagerState.animateScrollToPage(2) }
                    }
                    2 -> TopSongsSlide(data.topSongs.take(5)) {
                        scope.launch { mainPagerState.animateScrollToPage(3) }
                    }
                    3 -> TopArtistsSlide(data.topArtists.take(5)) {
                        scope.launch { mainPagerState.animateScrollToPage(4) }
                    }
                    4 -> TopAlbumsSlide(data.topAlbums.take(3)) {
                        scope.launch { mainPagerState.animateScrollToPage(5) }
                    }
                    5 -> MonthlyStatsSlide(data.monthlyStats) {
                        scope.launch { mainPagerState.animateScrollToPage(6) }
                    }
                    6 -> DailyPatternsSlide(data.dailyStats) {
                        scope.launch { mainPagerState.animateScrollToPage(7) }
                    }
                    7 -> HourlyPatternsSlide(data.hourlyStats) {
                        scope.launch { mainPagerState.animateScrollToPage(8) }
                    }
                    8 -> BestOfAllSlide(data) {
                        scope.launch { mainPagerState.animateScrollToPage(9) }
                    }
                    9 -> DonateSlide {
                        // Go back to first slide
                        scope.launch { mainPagerState.animateScrollToPage(0) }
                    }
                }
            }
            
            // Page indicators at bottom
            PageIndicator(
                pageCount = 10,
                currentPage = mainPagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            )
            
            // Navigation buttons
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Previous button
                    if (mainPagerState.currentPage > 0) {
                        Button(
                            onClick = {
                                scope.launch {
                                    mainPagerState.animateScrollToPage(mainPagerState.currentPage - 1)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.2f)
                            ),
                            shape = CircleShape,
                            modifier = Modifier.size(50.dp)
                        ) {
                            Text("←", fontSize = 20.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.size(50.dp))
                    }
                    
                    // Share/Next button
                    if (mainPagerState.currentPage < 9) {
                        Button(
                            onClick = {
                                scope.launch {
                                    mainPagerState.animateScrollToPage(mainPagerState.currentPage + 1)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.8f)
                            ),
                            shape = CircleShape,
                            modifier = Modifier.size(50.dp)
                        ) {
                            Text("→", fontSize = 20.sp, color = Color.Black)
                        }
                    } else {
                        // Use the context captured at the beginning of the function
                        Button(
                            onClick = {
                                shareRewindData(context, data, username, userRanking, currentYear)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1DB954)
                            ),
                            shape = CircleShape,
                            modifier = Modifier.size(50.dp)
                        ) {
                            Text("📤", fontSize = 20.sp)
                        }
                    }
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

// SLIDE 1: Welcome Screen
@Composable
fun WelcomeSlide(username: String, year: Int, onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.3f),
                            Color.White.copy(alpha = 0.1f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🎵",
                fontSize = 60.sp
            )
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Text(
            text = "YOUR ${year}",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 2.sp
        )
        
        Text(
            text = "MUSIC REWIND",
            fontSize = 42.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 10.dp)
        )
        
        Spacer(modifier = Modifier.height(30.dp))
        
        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(color = Color.White.copy(alpha = 0.8f))) {
                    append("For ")
                }
                withStyle(style = SpanStyle(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )) {
                    append(username)
                }
            },
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Text(
            text = "Swipe → to begin your journey",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }
}

// SLIDE 2: Stats & Ranking
@Composable
fun StatsSlide(
    username: String,
    data: RewindData,
    userRanking: String,
    userPercentile: String,
    showMinutesInHours: Boolean,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "📊 YOUR YEAR IN NUMBERS",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 30.dp)
        )
        
        // Ranking Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.15f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🏆 YOUR RANKING",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = userRanking,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = userPercentile,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        
        // Stats Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatItem(
                value = data.stats.totalPlays.toString(),
                label = "Total Plays",
                icon = "▶️",
                modifier = Modifier.weight(1f)
            )
            
            StatItem(
                value = if (showMinutesInHours) {
                    val hours = data.stats.totalMinutes / 60
                    val minutes = data.stats.totalMinutes % 60
                    "$hours h"
                } else {
                    "${data.stats.totalMinutes}"
                },
                label = if (showMinutesInHours) "Hours" else "Minutes",
                icon = "⏱️",
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatItem(
                value = data.totalUniqueSongs.toString(),
                label = "Unique Songs",
                icon = "🎶",
                modifier = Modifier.weight(1f)
            )
            
            StatItem(
                value = data.daysWithMusic.toString(),
                label = "Active Days",
                icon = "📅",
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(30.dp))
        
        // More stats
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatRow(icon = "👥", label = "Unique Artists", value = data.totalUniqueArtists.toString())
            StatRow(icon = "💿", label = "Unique Albums", value = data.totalUniqueAlbums.toString())
            StatRow(icon = "📋", label = "Unique Playlists", value = data.totalUniquePlaylists.toString())
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = "Swipe → for Top Songs",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }
}

@Composable
fun StatItem(value: String, label: String, icon: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
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
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = value,
                fontSize = 22.sp,
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
fun StatRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = icon, fontSize = 20.sp)
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp
            )
        }
        Text(
            text = value,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// SLIDE 3: Top Songs
@Composable
fun TopSongsSlide(songs: List<TopSong>, onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🔥 TOP SONGS OF THE YEAR",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 30.dp)
        )
        
        songs.forEachIndexed { index, song ->
            TopSongItemSlide(
                rank = index + 1,
                song = song,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        if (songs.isNotEmpty()) {
            Text(
                text = "\"${songs[0].song.title}\" was your most played song",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = "Swipe → for Top Artists",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }
}

@Composable
fun TopSongItemSlide(rank: Int, song: TopSong, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        when (rank) {
                            1 -> Color(0xFFFFD700)
                            2 -> Color(0xFFC0C0C0)
                            3 -> Color(0xFFCD7F32)
                            else -> Color.White.copy(alpha = 0.3f)
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
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.song.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
                Text(
                    text = song.song.artistsText ?: "Unknown Artist",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${song.playCount}",
                    fontSize = 16.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "plays",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// SLIDE 4: Top Artists
@Composable
fun TopArtistsSlide(artists: List<TopArtist>, onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "⭐ TOP ARTISTS OF THE YEAR",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 30.dp)
        )
        
        artists.forEachIndexed { index, artist ->
            TopArtistItemSlide(
                rank = index + 1,
                artist = artist,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        if (artists.isNotEmpty()) {
            Text(
                text = "\"${artists[0].artist.name ?: "Your top artist"}\" was your most listened artist",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = "Swipe → for Top Albums",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }
}

@Composable
fun TopArtistItemSlide(rank: Int, artist: TopArtist, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (rank) {
                        1 -> "🥇"
                        2 -> "🥈"
                        3 -> "🥉"
                        else -> "$rank"
                    },
                    fontSize = if (rank <= 3) 20.sp else 16.sp
                )
            }
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = artist.artist.name ?: "Unknown Artist",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${artist.songCount} songs • ${artist.minutes} min",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// SLIDE 5: Top Albums
@Composable
fun TopAlbumsSlide(albums: List<TopAlbum>, onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "💿 TOP ALBUMS OF THE YEAR",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 30.dp)
        )
        
        albums.forEachIndexed { index, album ->
            TopAlbumItemSlide(
                rank = index + 1,
                album = album,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        if (albums.isNotEmpty()) {
            Text(
                text = "\"${albums[0].album.title ?: "Your top album"}\" was your most played album",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = "Swipe → for Monthly Breakdown",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }
}

@Composable
fun TopAlbumItemSlide(rank: Int, album: TopAlbum, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "💿", fontSize = 24.sp)
            }
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = album.album.title ?: "Unknown Album",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.album.authorsText ?: "Unknown Artist",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${album.songCount} songs • ${album.minutes} min",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$rank",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

// SLIDE 6: Monthly Stats
@Composable
fun MonthlyStatsSlide(monthlyStats: List<MonthlyStat>, onNext: () -> Unit) {
    val sortedStats = monthlyStats.sortedBy { it.month }
    val peakMonth = monthlyStats.maxByOrNull { it.minutes }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "📈 MONTHLY LISTENING BREAKDOWN",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 30.dp)
        )
        
        if (peakMonth != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.2f)
                ),
                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🏆 PEAK MONTH",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = getMonthName(peakMonth.month),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Text(
                        text = "${peakMonth.minutes} minutes",
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Text(
                        text = "${peakMonth.plays} plays",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        
        // Monthly list
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sortedStats.forEach { monthStat ->
                MonthlyStatItemSlide(monthStat, isPeak = monthStat == peakMonth)
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Text(
            text = "Swipe → for Daily Patterns",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }
}

fun getMonthName(monthNumber: String): String {
    return try {
        val monthInt = monthNumber.toInt()
        monthNames[monthInt] ?: "Month $monthNumber"
    } catch (e: Exception) {
        "Month $monthNumber"
    }
}

@Composable
fun MonthlyStatItemSlide(monthStat: MonthlyStat, isPeak: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPeak) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = getMonthName(monthStat.month),
                fontSize = 16.sp,
                fontWeight = if (isPeak) FontWeight.Bold else FontWeight.Medium,
                color = Color.White
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${monthStat.minutes}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "minutes",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${monthStat.plays}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "plays",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// SLIDE 7: Daily Patterns
@Composable
fun DailyPatternsSlide(dailyStats: List<DailyStat>, onNext: () -> Unit) {
    val sortedStats = dailyStats.sortedByDescending { it.minutes }
    val top3Days = sortedStats.take(3)
    val weekOrder = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "📅 DAILY LISTENING PATTERNS",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 30.dp)
        )
        
        // Top 3 days highlight
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.2f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🏅 TOP 3 DAYS",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    top3Days.forEachIndexed { index, dayStat ->
                        TopDayItem(rank = index + 1, dayStat = dayStat)
                    }
                }
            }
        }
        
        // All days in week order
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            weekOrder.forEach { dayCode ->
                val dayStat = dailyStats.find { it.dayOfWeek == dayCode }
                DayStatItem(
                    dayName = dayNames[dayCode] ?: dayCode,
                    minutes = dayStat?.minutes ?: 0L,
                    plays = dayStat?.plays ?: 0,
                    isTop3 = top3Days.any { it.dayOfWeek == dayCode }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        if (top3Days.isNotEmpty()) {
            Text(
                text = "You listened most on ${dayNames[top3Days[0].dayOfWeek] ?: top3Days[0].dayOfWeek}",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Text(
            text = "Swipe → for Hourly Patterns",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }
}

@Composable
fun TopDayItem(rank: Int, dayStat: DailyStat) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(
                    when (rank) {
                        1 -> Color(0xFFFFD700)
                        2 -> Color(0xFFC0C0C0)
                        3 -> Color(0xFFCD7F32)
                        else -> Color.White.copy(alpha = 0.3f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when (rank) {
                    1 -> "🥇"
                    2 -> "🥈"
                    3 -> "🥉"
                    else -> "$rank"
                },
                fontSize = if (rank <= 3) 24.sp else 20.sp
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = dayNames[dayStat.dayOfWeek]?.take(3) ?: dayStat.dayOfWeek,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Text(
            text = "${dayStat.minutes} min",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun DayStatItem(dayName: String, minutes: Long, plays: Int, isTop3: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isTop3) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f)
        ),
        border = if (isTop3) BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = dayName,
                fontSize = 16.sp,
                fontWeight = if (isTop3) FontWeight.Bold else FontWeight.Medium,
                color = Color.White
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${minutes}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "minutes",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$plays",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "plays",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// SLIDE 8: Hourly Patterns
@Composable
fun HourlyPatternsSlide(hourlyStats: List<HourlyStat>, onNext: () -> Unit) {
    val sortedStats = hourlyStats.sortedByDescending { it.minutes }
    val peakHour = sortedStats.firstOrNull()
    val leastHour = hourlyStats.minByOrNull { it.minutes }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "⏰ HOURLY LISTENING PATTERNS",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 30.dp)
        )
        
        // Peak vs Least comparison
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Peak Hour
            HourComparisonCard(
                title = "PEAK HOUR",
                hour = peakHour?.hour ?: "N/A",
                minutes = peakHour?.minutes ?: 0L,
                description = "When you listened the most",
                isPeak = true,
                modifier = Modifier.weight(1f)
            )
            
            // Least Active Hour
            HourComparisonCard(
                title = "QUIET HOUR",
                hour = leastHour?.hour ?: "N/A",
                minutes = leastHour?.minutes ?: 0L,
                description = "When you listened the least",
                isPeak = false,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Top hours list
        Text(
            text = "TOP LISTENING HOURS",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.7f),
            letterSpacing = 1.sp,
            modifier = Modifier
                .padding(bottom = 12.dp)
                .align(Alignment.Start)
        )
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sortedStats.take(6).forEachIndexed { index, hourStat ->
                HourStatItem(
                    hour = hourStat.hour,
                    description = hourDescriptions[hourStat.hour] ?: "",
                    minutes = hourStat.minutes,
                    plays = hourStat.plays,
                    rank = index + 1
                )
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        peakHour?.let {
            val description = hourDescriptions[it.hour] ?: ""
            Text(
                text = "You were most active at $description (${it.hour})",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Text(
            text = "Swipe → for Best of Everything",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }
}

@Composable
fun HourComparisonCard(
    title: String,
    hour: String,
    minutes: Long,
    description: String,
    isPeak: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPeak) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f)
        ),
        border = BorderStroke(
            2.dp,
            if (isPeak) Color(0xFFFFD700) else Color.White.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = hour,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Text(
                text = "${minutes} min",
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            Text(
                text = description,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun HourStatItem(hour: String, description: String, minutes: Long, plays: Int, rank: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$rank",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                Column {
                    Text(
                        text = hour,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Text(
                        text = description,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${minutes} min",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "$plays plays",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// SLIDE 9: Best of Everything
@Composable
fun BestOfAllSlide(data: RewindData, onNext: () -> Unit) {
    val topSong = data.topSongs.firstOrNull()
    val topArtist = data.topArtists.firstOrNull()
    val topAlbum = data.topAlbums.firstOrNull()
    val topMonth = data.monthlyStats.maxByOrNull { it.minutes }
    val topDay = data.dailyStats.maxByOrNull { it.minutes }
    val topHour = data.hourlyStats.maxByOrNull { it.minutes }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🏆 BEST OF EVERYTHING",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        
        Text(
            text = "Your Year in Highlights",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 30.dp)
        )
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Song
            BestOfItem(
                icon = "🎵",
                title = "TOP SONG",
                value = topSong?.song?.title ?: "N/A",
                detail = topSong?.song?.artistsText ?: "",
                extra = "${topSong?.playCount ?: 0} plays"
            )
            
            // Artist
            BestOfItem(
                icon = "⭐",
                title = "TOP ARTIST",
                value = topArtist?.artist?.name ?: "N/A",
                detail = "${topArtist?.songCount ?: 0} songs",
                extra = "${topArtist?.minutes ?: 0} min"
            )
            
            // Album
            BestOfItem(
                icon = "💿",
                title = "TOP ALBUM",
                value = topAlbum?.album?.title ?: "N/A",
                detail = topAlbum?.album?.authorsText ?: "",
                extra = "${topAlbum?.minutes ?: 0} min"
            )
            
            // Month
            BestOfItem(
                icon = "📈",
                title = "PEAK MONTH",
                value = topMonth?.let { getMonthName(it.month) } ?: "N/A",
                detail = "${topMonth?.plays ?: 0} plays",
                extra = "${topMonth?.minutes ?: 0} min"
            )
            
            // Day
            BestOfItem(
                icon = "📅",
                title = "TOP DAY",
                value = topDay?.let { dayNames[it.dayOfWeek] ?: it.dayOfWeek } ?: "N/A",
                detail = "${topDay?.plays ?: 0} plays",
                extra = "${topDay?.minutes ?: 0} min"
            )
            
            // Hour
            BestOfItem(
                icon = "⏰",
                title = "PEAK HOUR",
                value = topHour?.hour ?: "N/A",
                detail = hourDescriptions[topHour?.hour] ?: "",
                extra = "${topHour?.minutes ?: 0} min"
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.2f)
            ),
            border = BorderStroke(2.dp, Color.White.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎶 TOTAL LISTENING",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${data.stats.totalMinutes}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text(
                    text = "minutes • ${data.stats.totalPlays} plays",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Text(
            text = "Swipe → for Support & Donate",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }
}

@Composable
fun BestOfItem(
    icon: String,
    title: String,
    value: String,
    detail: String,
    extra: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = icon,
                fontSize = 24.sp
            )
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (detail.isNotEmpty()) {
                    Text(
                        text = detail,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Text(
                text = extra,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

// SLIDE 10: Donate Slide
@Composable
fun DonateSlide(onNext: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "❤️ SUPPORT THE DEVELOPER",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        
        Text(
            text = "Anon Ghost",
            fontSize = 20.sp,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 5.dp)
        )
        
        Text(
            text = "I build mobile apps & websites 🚀",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 30.dp)
        )
        
        // Donation options
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.15f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ONE-TIME DONATION",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Button(
                    onClick = {
                        // Open Ko-fi page
                        uriHandler.openUri("https://ko-fi.com/anonghost40418")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF29ABE0)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "☕", fontSize = 20.sp)
                        Text(
                            text = "Buy a Coffee for Anon Ghost",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "ko-fi.com/anonghost40418",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
        
        Text(
            text = "Your support keeps me motivated and helps me create even more awesome projects!",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // PayPal logo placeholder
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF003087)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "P", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                
                // Mastercard logo placeholder
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEB001B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "MC", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                
                // Visa logo placeholder
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1F71)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "VISA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                Text(
                    text = "Pay Anon Ghost directly",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = "Thank you for using Cubic Music! 🎵",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Text(
            text = "Swipe ← to go back to start",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }
}

@Composable
fun PageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
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

suspend fun fetchSongThumbnail(songTitle: String, artist: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val query = URLEncoder.encode("$songTitle $artist", "UTF-8")
            val url = "https://yt.omada.cafe/api/v1/search?q=$query&type=video"
            
            val connection = URL(url).openConnection()
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            
            val response = connection.getInputStream().bufferedReader().use { it.readText() }
            
            val thumbnailMatch = Regex("\"videoThumbnails\":\\s*\\[.*?\"url\":\"(.*?)\"").find(response)
            thumbnailMatch?.groups?.get(1)?.value?.let { thumbnailUrl ->
                val cleanedUrl = thumbnailUrl.replace("\\/", "/")
                return@withContext if (cleanedUrl.startsWith("//")) {
                    "https:$cleanedUrl"
                } else if (!cleanedUrl.startsWith("http")) {
                    "https://$cleanedUrl"
                } else {
                    cleanedUrl
                }
            }
        } catch (e: Exception) {
        }
        return@withContext null
    }
}

suspend fun fetchArtistThumbnail(artistName: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val query = URLEncoder.encode(artistName, "UTF-8")
            val url = "https://yt.omada.cafe/api/v1/search?q=$query&type=channel"
            
            val connection = URL(url).openConnection()
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            
            val response = connection.getInputStream().bufferedReader().use { it.readText() }
            
            val thumbnailMatch = Regex("\"authorThumbnails\":\\s*\\[.*?\"url\":\"(.*?)\"").find(response)
            thumbnailMatch?.groups?.get(1)?.value?.let { thumbnailUrl ->
                val cleanedUrl = thumbnailUrl.replace("\\/", "/")
                return@withContext if (cleanedUrl.startsWith("//")) {
                    "https:$cleanedUrl"
                } else if (!cleanedUrl.startsWith("http")) {
                    "https://$cleanedUrl"
                } else {
                    cleanedUrl
                }
            }
        } catch (e: Exception) {
        }
        return@withContext null
    }
}

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
                    connection.connectTimeout = 2000
                    connection.readTimeout = 2000
                    val inputStream = connection.getInputStream()
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    bitmap?.asImageBitmap()
                } catch (e: Exception) {
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
            Text(
                text = if (contentDescription?.contains("artist") == true) "🎤" else "🎵",
                fontSize = 20.sp
            )
        }
    }
}

private fun calculateUserRanking(data: RewindData?, joinDate: String): Pair<String, String> {
    if (data == null) return Pair("New Listener", "Just Starting")
    
    val totalMinutes = data.stats.totalMinutes
    val totalPlays = data.stats.totalPlays
    val uniqueSongs = data.totalUniqueSongs
    val daysWithMusic = data.daysWithMusic
    
    var score = 0
    
    val hoursListened = totalMinutes / 60.0
    score += when {
        hoursListened >= 1000 -> 40
        hoursListened >= 500 -> 35
        hoursListened >= 200 -> 30
        hoursListened >= 100 -> 25
        hoursListened >= 50 -> 20
        hoursListened >= 20 -> 15
        hoursListened >= 10 -> 10
        hoursListened >= 5 -> 5
        else -> 1
    }
    
    score += when {
        totalPlays >= 10000 -> 40
        totalPlays >= 5000 -> 35
        totalPlays >= 2000 -> 30
        totalPlays >= 1000 -> 25
        totalPlays >= 500 -> 20
        totalPlays >= 200 -> 15
        totalPlays >= 100 -> 10
        totalPlays >= 50 -> 5
        else -> 1
    }
    
    score += when {
        uniqueSongs >= 1000 -> 30
        uniqueSongs >= 500 -> 25
        uniqueSongs >= 200 -> 20
        uniqueSongs >= 100 -> 15
        uniqueSongs >= 50 -> 10
        uniqueSongs >= 20 -> 5
        else -> 1
    }
    
    val activeDaysPercent = (daysWithMusic / 365.0) * 100
    score += when {
        activeDaysPercent >= 90 -> 30
        activeDaysPercent >= 75 -> 25
        activeDaysPercent >= 50 -> 20
        activeDaysPercent >= 30 -> 15
        activeDaysPercent >= 20 -> 10
        activeDaysPercent >= 10 -> 5
        else -> 1
    }
    
    if (joinDate.isNotEmpty()) {
        try {
            val joinYear = joinDate.substring(0, 4).toInt()
            val currentYear = LocalDate.now().year
            val yearsActive = currentYear - joinYear
            
            score += when {
                yearsActive >= 10 -> 20
                yearsActive >= 5 -> 15
                yearsActive >= 3 -> 10
                yearsActive >= 2 -> 5
                yearsActive >= 1 -> 2
                else -> 1
            }
        } catch (e: Exception) {
            score += 1
        }
    }
    
    return when {
        score >= 140 -> Pair("Legendary Listener", "Top 1%")
        score >= 120 -> Pair("Music Maestro", "Top 3%")
        score >= 100 -> Pair("Audio Aficionado", "Top 5%")
        score >= 80 -> Pair("Melody Master", "Top 10%")
        score >= 60 -> Pair("Music Enthusiast", "Top 20%")
        score >= 40 -> Pair("Regular Listener", "Top 35%")
        score >= 20 -> Pair("Casual Listener", "Top 50%")
        score >= 10 -> Pair("New Explorer", "Top 75%")
        else -> Pair("Just Starting", "Top 90%")
    }
}

private fun encodeRewindDataForUrl(data: RewindData?, username: String, year: Int): String {
    if (data == null) return ""
    
    return try {
        val jsonString = buildString {
            append("{")
            append("\"username\":\"${escapeJsonString(username)}\",")
            append("\"year\":\"${year}\",")
            append("\"totalPlays\":${data.stats.totalPlays},")
            append("\"totalMinutes\":${data.stats.totalMinutes},")
            append("\"uniqueSongs\":${data.totalUniqueSongs},")
            append("\"uniqueArtists\":${data.totalUniqueArtists},")
            append("\"uniqueAlbums\":${data.totalUniqueAlbums},")
            append("\"uniquePlaylists\":${data.totalUniquePlaylists},")
            append("\"daysWithMusic\":${data.daysWithMusic},")
            append("\"averageDailyMinutes\":${data.stats.averageDailyMinutes},")
            
            append("\"topSongs\":[")
            data.topSongs.take(10).forEachIndexed { index, song ->
                if (index > 0) append(",")
                append("{")
                append("\"title\":\"${escapeJsonString(song.song.title)}\",")
                append("\"artist\":\"${escapeJsonString(song.song.artistsText ?: "")}\",")
                append("\"plays\":${song.playCount},")
                append("\"minutes\":${song.minutes}")
                append("}")
            }
            append("],")
            
            append("\"topArtists\":[")
            data.topArtists.take(10).forEachIndexed { index, artist ->
                if (index > 0) append(",")
                append("{")
                append("\"name\":\"${escapeJsonString(artist.artist.name ?: "")}\",")
                append("\"minutes\":${artist.minutes},")
                append("\"songCount\":${artist.songCount}")
                append("}")
            }
            append("],")
            
            append("\"topAlbums\":[")
            data.topAlbums.take(5).forEachIndexed { index, album ->
                if (index > 0) append(",")
                append("{")
                append("\"title\":\"${escapeJsonString(album.album.title ?: "")}\",")
                append("\"artist\":\"${escapeJsonString(album.album.authorsText ?: "")}\",")
                append("\"minutes\":${album.minutes},")
                append("\"songCount\":${album.songCount}")
                append("}")
            }
            append("],")
            
            append("\"topPlaylists\":[")
            data.topPlaylists.take(5).forEachIndexed { index, playlist ->
                if (index > 0) append(",")
                append("{")
                append("\"name\":\"${escapeJsonString(playlist.playlist.playlist.name)}\",")
                append("\"minutes\":${playlist.minutes},")
                append("\"songCount\":${playlist.songCount}")
                append("}")
            }
            append("],")
            
            append("\"listeningPatterns\":{")
            data.stats.mostActiveDay?.let {
                append("\"mostActiveDay\":\"${escapeJsonString(it.dayOfWeek)}\",")
            }
            data.stats.mostActiveHour?.let {
                append("\"mostActiveHour\":\"${escapeJsonString(it.hour)}\",")
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
            append("}")
            
            append("}")
        }
        URLEncoder.encode(jsonString, "UTF-8")
    } catch (e: Exception) {
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

private fun shareRewindData(context: Context, data: RewindData?, username: String, ranking: String, year: Int) {
    if (data == null) return
    
    val shareText = buildString {
        append("🎵 My $year Music Rewind 🎵\n\n")
        append("🎤 Username: $username\n")
        append("🏆 Ranking: $ranking\n\n")
        append("📊 Stats:\n")
        append("• Total Plays: ${data.stats.totalPlays}\n")
        append("• Total Listening: ${data.stats.totalMinutes} minutes\n")
        append("• Unique Songs: ${data.totalUniqueSongs}\n")
        append("• Active Days: ${data.daysWithMusic}\n")
        
        if (data.stats.mostActiveDay != null) {
            append("• Most Active Day: ${data.stats.mostActiveDay.dayOfWeek}\n")
        }
        if (data.stats.mostActiveHour != null) {
            append("• Most Active Hour: ${data.stats.mostActiveHour.hour}\n")
        }
        if (data.stats.lastPlayDate != null) {
            append("• Last Played: ${data.stats.lastPlayDate}\n")
        }
        
        append("\n")
        
        if (data.topSongs.isNotEmpty()) {
            append("🔥 Top Songs:\n")
            data.topSongs.take(5).forEachIndexed { index, song ->
                append("${index + 1}. ${song.song.title} - ${song.song.artistsText ?: "Unknown Artist"}\n")
            }
            append("\n")
        }
        
        if (data.topArtists.isNotEmpty()) {
            append("⭐ Top Artists:\n")
            data.topArtists.take(5).forEachIndexed { index, artist ->
                append("${index + 1}. ${artist.artist.name ?: "Unknown Artist"}\n")
            }
        }
        
        append("\n🎶 Generated with Cubic Rewind")
    }
    
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
        putExtra(Intent.EXTRA_SUBJECT, "My $year Music Rewind")
    }
    
    context.startActivity(Intent.createChooser(shareIntent, "Share Your Rewind"))
}