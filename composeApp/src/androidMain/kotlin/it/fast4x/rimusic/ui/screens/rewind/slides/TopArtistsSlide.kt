package it.fast4x.rimusic.ui.screens.rewind.slides

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.rememberImagePainter
import coil.request.ImageRequest
import com.mikepenz.hypnoticcanvas.shaders.OilFlow
import it.fast4x.rimusic.ui.screens.rewind.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLEncoder
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.time.LocalDate
import androidx.compose.foundation.border
import com.mikepenz.hypnoticcanvas.shaderBackground

@Composable
fun TopArtistsSlide(artists: List<TopArtist>, onNext: () -> Unit) {
    // 获取第一个艺术家作为主要展示
    val topArtist = artists.firstOrNull()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .shaderBackground(OilFlow)  // 使用OilFlow背景
    ) {
        // 深色覆盖层增强可读性
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.6f),
                            Color.Black.copy(alpha = 0.8f)
                        )
                    )
                )
        )
        
        // 主要内容区域
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(30.dp))
            
            // 标题区域
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 30.dp)
            ) {
                // 装饰性标题
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x80643AB7))  // 紫色半透明
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🎤 TOP ARTISTS",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "YOUR MUSIC TASTE IN ${LocalDate.now().year}",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
            
            if (topArtist != null) {
                // 顶部艺术家展示
                TopArtistHighlightCard(topArtist = topArtist)
                Spacer(modifier = Modifier.height(30.dp))
            }
            
            // 艺术家列表标题
            Text(
                text = "YOUR TOP 10 ARTISTS",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE1BEE7),
                letterSpacing = 0.8.sp,
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .fillMaxWidth()
                    .align(Alignment.Start)
            )
            
            // 艺术家列表
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(artists.take(10)) { index, artist ->
                    ArtistCardItem(
                        rank = index + 1,
                        artist = artist,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(30.dp))
            
            // 总统计
            if (artists.isNotEmpty()) {
                val totalArtists = artists.size
                val totalMinutes = artists.sumOf { it.minutes }
                val totalHours = (totalMinutes / 60f).toInt()
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0x805C6BC0)  // 紫色-蓝色半透明
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "📊 YEAR IN REVIEW",
                            fontSize = 12.sp,
                            color = Color(0xFFFFD54F),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$totalArtists",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text(
                                    text = "ARTISTS",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${artists.sumOf { it.songCount }}",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text(
                                    text = "SONGS",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${totalHours}h",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text(
                                    text = "LISTENED",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
            }
            
            // 导航提示
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(bottom = 30.dp)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "→",
                        fontSize = 20.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "swipe for top albums",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun TopArtistHighlightCard(topArtist: TopArtist) {
    var thumbnailUrl by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(topArtist) {
        // 异步获取艺术家缩略图
        thumbnailUrl = fetchArtistThumbnail(
            topArtist.artist.name ?: "",
            "artist"
        )
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x809C27B0)  // 紫色半透明
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 排名徽章
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFD700),
                                Color(0xFFFFB300)
                            )
                        )
                    )
                    .shadow(8.dp, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🥇",
                    fontSize = 30.sp
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 艺术家头像
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .shadow(12.dp, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailUrl != null) {
                    SubcomposeAsyncImage(
                        model = thumbnailUrl,
                        contentDescription = "${topArtist.artist.name} thumbnail",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF512DA8),
                                                Color(0xFF311B92)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White.copy(alpha = 0.7f),
                                    strokeWidth = 3.dp
                                )
                            }
                        },
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF512DA8),
                                                Color(0xFF311B92)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🎤",
                                    fontSize = 40.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF512DA8),
                                        Color(0xFF311B92)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🎤",
                            fontSize = 50.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                
                // 金色边框
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                        .border(
                            width = 3.dp,
                            color = Color(0xFFFFD700).copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 艺术家信息
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = topArtist.artist.name ?: "Unknown Artist",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "YOUR #1 ARTIST",
                    fontSize = 12.sp,
                    color = Color(0xFFFFD700),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 统计数据
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    value = "${topArtist.songCount}",
                    label = "SONGS",
                    color = Color(0xFF4FC3F7)
                )
                StatItem(
                    value = "${topArtist.minutes}",
                    label = "MINUTES",
                    color = Color(0xFF81C784)
                )
                StatItem(
                    value = "${String.format("%.1f", topArtist.songCount / 365.0)}",
                    label = "PER DAY",
                    color = Color(0xFFFFB74D)
                )
            }
        }
    }
}

@Composable
private fun ArtistCardItem(rank: Int, artist: TopArtist, modifier: Modifier = Modifier) {
    var thumbnailUrl by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(artist) {
        thumbnailUrl = fetchArtistThumbnail(
            artist.artist.name ?: "",
            "music"
        )
    }
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x80703AB7)  // 紫色半透明
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 排名徽章
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        when (rank) {
                            1 -> Color(0xFFFFD700).copy(alpha = 0.9f)
                            2 -> Color(0xFFC0C0C0).copy(alpha = 0.9f)
                            3 -> Color(0xFFCD7F32).copy(alpha = 0.9f)
                            else -> Color(0xFF6A1B9A).copy(alpha = 0.8f)
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
                    fontSize = when (rank) {
                        in 1..3 -> 18.sp
                        else -> 16.sp
                    },
                    fontWeight = FontWeight.Bold,
                    color = when (rank) {
                        in 1..3 -> Color.Black
                        else -> Color.White
                    }
                )
            }
            
            // 艺术家头像
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF311B92).copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailUrl != null) {
                    SubcomposeAsyncImage(
                        model = thumbnailUrl,
                        contentDescription = "${artist.artist.name} thumbnail",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        loading = {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        },
                        error = {
                            Text(
                                text = "🎵",
                                fontSize = 24.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    )
                } else {
                    Text(
                        text = "🎤",
                        fontSize = 24.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            
            // 艺术家信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = artist.artist.name ?: "Unknown Artist",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${artist.songCount} songs",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "${artist.minutes} min",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
            
            // 播放图标
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFD700).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "▶",
                    fontSize = 14.sp,
                    color = Color(0xFFFFD700)
                )
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp
        )
    }
}

// 获取艺术家缩略图的函数
private suspend fun fetchArtistThumbnail(artistName: String, searchType: String = "artist"): String? {
    return withContext(Dispatchers.IO) {
        try {
            val query = URLEncoder.encode("$artistName $searchType", "UTF-8")
            val url = "https://yt.omada.cafe/api/v1/search?q=$query&type=video"
            
            val connection = URL(url).openConnection()
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            
            // 简化的正则匹配，查找缩略图URL
            val pattern = Regex("\"videoThumbnails\"\\s*:\\s*\\[.*?\\{\\s*\"url\"\\s*:\\s*\"(https?://[^\"]+)\"")
            val match = pattern.find(response)
            
            match?.groups?.get(1)?.value?.let { thumbnailUrl ->
                return@withContext thumbnailUrl.replace("\\/", "/")
            }
            
            // 备用模式
            val backupPattern = Regex("\"url\"\\s*:\\s*\"(https?://[^\"]+\\.(jpg|png|webp))\"")
            val backupMatch = backupPattern.find(response)
            
            backupMatch?.groups?.get(1)?.value?.let { thumbnailUrl ->
                return@withContext thumbnailUrl.replace("\\/", "/")
            }
            
            return@withContext null
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}


