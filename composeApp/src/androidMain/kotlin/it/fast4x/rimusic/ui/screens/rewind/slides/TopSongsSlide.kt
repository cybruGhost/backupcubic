package it.fast4x.rimusic.ui.screens.rewind.slides

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import it.fast4x.rimusic.ui.screens.rewind.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopSongsSlide(songs: List<TopSong>, onNext: () -> Unit) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val thumbnailUrls = remember { mutableStateMapOf<String, String?>() }
    
    // Fetch thumbnails for all songs using the API
    LaunchedEffect(songs) {
        songs.forEach { song ->
            val searchQuery = "${song.song.title} ${song.song.artistsText ?: ""}"
            coroutineScope.launch {
                try {
                    val encodedQuery = java.net.URLEncoder.encode(searchQuery, "UTF-8")
                    val url = "https://yt.omada.cafe/api/v1/search?q=$encodedQuery"
                    val jsonText = URL(url).readText()
                    val jsonArray = JSONArray(jsonText)
                    if (jsonArray.length() > 0) {
                        val firstResult = jsonArray.getJSONObject(0)
                        val thumbnails = firstResult.optJSONObject("thumbnails")
                        if (thumbnails != null) {
                            // Get medium quality thumbnail if available
                            val thumbnailUrl = thumbnails.optString("medium", null) ?: 
                                             thumbnails.optString("high", null) ?:
                                             thumbnails.optString("default", null)
                            if (thumbnailUrl != null) {
                                thumbnailUrls[song.song.id] = thumbnailUrl
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0519),
                        Color(0xFF1A0F2E),
                        Color(0xFF0A0519)
                    )
                )
            )
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        
        // Decorative header with emojis
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 30.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background glow effect
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF00D4FF).copy(alpha = 0.1f),
                                Color.Transparent
                            ),
                            center = Offset.Unspecified,
                            radius = 200.dp.value
                        )
                    )
            )
            
            // Main header with emoji decoration
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Decorative emojis
                    Text(
                        text = "🎵 ",
                        fontSize = 32.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    
                    Text(
                        text = "YOUR TOP 10 SONGS",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF00D4FF).copy(alpha = 0.3f),
                                        Color(0xFFE040FB).copy(alpha = 0.3f),
                                        Color(0xFF00D4FF).copy(alpha = 0.3f)
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                            .shadow(
                                elevation = 20.dp,
                                shape = RoundedCornerShape(20.dp),
                                clip = true
                            )
                    )
                    
                    Text(
                        text = " 🎶",
                        fontSize = 32.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Subtitle with emoji
                Text(
                    text = "🎧 Your year in music 🎤",
                    fontSize = 14.sp,
                    color = Color(0xFF00D4FF),
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        if (songs.isEmpty()) {
            Spacer(modifier = Modifier.height(100.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎵",
                    fontSize = 48.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "No songs data available",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 16.sp
                )
                Text(
                    text = "Start listening to see your stats!",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            // Top 3 songs with special styling
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🏆 TOP 3 🏆",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700),
                    modifier = Modifier
                        .padding(bottom = 20.dp)
                        .background(
                            color = Color(0xFFFFD700).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                songs.take(3).forEachIndexed { index, song ->
                    Top3SongItem(
                        rank = index + 1,
                        song = song,
                        thumbnailUrl = thumbnailUrls[song.song.id],
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(30.dp))
            
            // Rest of the songs (4-10)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎯 THE TOP 10 🎯",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .padding(bottom = 20.dp)
                        .background(
                            color = Color(0xFF00D4FF).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                songs.drop(3).forEachIndexed { index, song ->
                    StandardSongItem(
                        rank = index + 4,
                        song = song,
                        thumbnailUrl = thumbnailUrls[song.song.id],
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Stats highlight with emoji decoration
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(25.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1035).copy(alpha = 0.9f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 15.dp),
                border = BorderStroke(1.dp, Color(0xFF00D4FF).copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Decorative background
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF00D4FF).copy(alpha = 0.05f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header with emoji
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = "✨ ",
                                fontSize = 24.sp
                            )
                            Text(
                                text = "YOUR MUSICAL JOURNEY",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00D4FF),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = " ✨",
                                fontSize = 24.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Highlight song
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = Color(0xFF00D4FF).copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(20.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "🎵",
                                    fontSize = 32.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    text = "\"${songs[0].song.title}\"",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                )
                                Text(
                                    text = "was your soundtrack of the year",
                                    fontSize = 16.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // Stats row with emojis
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(
                                value = "${songs[0].playCount}",
                                label = "PLAYS",
                                emoji = "🔥",
                                color = Color(0xFFFFD700)
                            )
                            StatItem(
                                value = "${songs.size}",
                                label = "TRACKS",
                                emoji = "🎵",
                                color = Color(0xFF00D4FF)
                            )
                            StatItem(
                                value = "${songs.sumOf { it.playCount }}",
                                label = "TOTAL PLAYS",
                                emoji = "📈",
                                color = Color(0xFFE040FB)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
        
        // Swipe hint with emoji
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "🎨 ",
                fontSize = 18.sp
            )
            Text(
                text = "Swipe for Top Artists",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color(0xFF00D4FF),
                modifier = Modifier
                    .rotate(90f)
                    .size(20.dp)
            )
        }
    }
}

@Composable
private fun Top3SongItem(
    rank: Int,
    song: TopSong,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier
) {
    val gradientColors = when (rank) {
        1 -> listOf(Color(0xFFFFD700), Color(0xFFFFA000))
        2 -> listOf(Color(0xFFC0C0C0), Color(0xFF9E9E9E))
        3 -> listOf(Color(0xFFCD7F32), Color(0xFF8B4513))
        else -> listOf(Color(0xFF6A11CB), Color(0xFF2575FC))
    }
    
    val rankEmoji = when (rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "#$rank"
    }
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(25.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 15.dp),
        border = BorderStroke(2.dp, gradientColors[0].copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = gradientColors.map { it.copy(alpha = 0.2f) },
                        start = Offset(0f, 0f),
                        end = Offset(1000f, 1000f)
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Rank badge with emoji
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .shadow(
                            elevation = 15.dp,
                            shape = CircleShape,
                            clip = true
                        )
                        .drawWithCache {
                            onDrawBehind {
                                // Draw metallic shine
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.4f),
                                            Color.Transparent
                                        ),
                                        center = Offset(size.width * 0.3f, size.height * 0.3f)
                                    ),
                                    radius = size.minDimension / 2
                                )
                            }
                        }
                        .background(
                            brush = Brush.radialGradient(
                                colors = gradientColors,
                                center = Offset.Unspecified,
                                radius = 100f
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = rankEmoji,
                        fontSize = 28.sp,
                        modifier = Modifier.shadow(2.dp)
                    )
                }
                
                // Thumbnail or emoji placeholder
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(
                            color = Color.Black.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(15.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbnailUrl != null) {
                        NetworkImage(
                            url = thumbnailUrl,
                            contentDescription = song.song.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(15.dp))
                        )
                    } else {
                        Text(
                            text = "🎵",
                            fontSize = 32.sp
                        )
                    }
                }
                
                // Song info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎧 ",
                            fontSize = 16.sp
                        )
                        Text(
                            text = song.song.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 22.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "👤 ",
                            fontSize = 12.sp
                        )
                        Text(
                            text = song.song.artistsText ?: "Unknown Artist",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    // Play count with decorative bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .background(
                                    color = gradientColors[0].copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "▶️ ",
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = "${song.playCount}",
                                    fontSize = 16.sp,
                                    color = gradientColors[0],
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "plays",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            
            // Decorative corner emoji
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.TopEnd)
                    .background(
                        color = gradientColors[0].copy(alpha = 0.1f),
                        shape = RoundedCornerShape(0.dp, 25.dp, 0.dp, 25.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⭐",
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun StandardSongItem(
    rank: Int,
    song: TopSong,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier
) {
    val gradientColors = when (rank) {
        in 4..6 -> listOf(Color(0xFF9C27B0), Color(0xFF7B1FA2))
        else -> listOf(Color(0xFF00D4FF), Color(0xFF0097A7))
    }
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        onClick = { /* Handle song click */ }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rank indicator with emoji
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        color = when (rank) {
                            in 4..6 -> Color(0xFF9C27B0).copy(alpha = 0.3f)
                            else -> Color(0xFF00D4FF).copy(alpha = 0.2f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (rank) {
                        4 -> "④"
                        5 -> "⑤"
                        6 -> "⑥"
                        7 -> "⑦"
                        8 -> "⑧"
                        9 -> "⑨"
                        10 -> "⑩"
                        else -> "$rank"
                    },
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            
            // Thumbnail or emoji
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF3D2E54),
                                Color(0xFF2A1F3A)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailUrl != null) {
                    NetworkImage(
                        url = thumbnailUrl,
                        contentDescription = song.song.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Text(
                        text = "🎵",
                        fontSize = 24.sp
                    )
                }
            }
            
            // Song info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎵 ",
                        fontSize = 12.sp
                    )
                    Text(
                        text = song.song.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "👤 ",
                        fontSize = 10.sp
                    )
                    Text(
                        text = song.song.artistsText ?: "Unknown Artist",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Play count
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "▶️ ",
                        fontSize = 10.sp
                    )
                    Text(
                        text = "${song.playCount}",
                        fontSize = 14.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "plays",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, emoji: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = emoji,
                fontSize = 20.sp,
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f),
            letterSpacing = 0.5.sp
        )
    }
}

// NetworkImage composable - with proper API fetching
@Composable
fun NetworkImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(url) {
        isLoading = true
        imageBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val inputStream = connection.getInputStream()
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                bitmap?.asImageBitmap()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        isLoading = false
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
        } else if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap!!,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            Text(
                text = "🎵",
                fontSize = 32.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}