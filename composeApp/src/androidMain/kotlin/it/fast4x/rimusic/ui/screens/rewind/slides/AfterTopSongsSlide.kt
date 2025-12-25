package it.fast4x.rimusic.ui.screens.rewind.slides

import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import it.fast4x.rimusic.ui.screens.rewind.TopSong
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.border


@Composable
fun AfterTopSongsSlide(
    topSong: TopSong? = null,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    // 状态管理：缩略图URL、预览URL、加载状态
    var thumbnailUrl by remember { mutableStateOf<String?>(null) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    // 创建并管理 MediaPlayer
    val mediaPlayer = remember { MediaPlayer() }
    
    // 自动获取歌曲信息并播放
DisposableEffect(topSong) {
    // Setup: launch coroutine to auto-play preview if available
    val job = CoroutineScope(IO).launch {
        topSong?.let { song ->
            val songInfo = fetchSongInfo(song.song.title, song.song.artistsText ?: "")
            previewUrl = songInfo.second
            previewUrl?.let { url ->
                try {
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(url)
                    mediaPlayer.prepareAsync()
                    mediaPlayer.setOnPreparedListener { player ->
                        player.start()
                        launch {
                            delay(30000)
                            withContext(Dispatchers.Main) {
                                if (player.isPlaying) player.pause()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Cleanup when Composable leaves composition
    onDispose {
        job.cancel() // cancel coroutine
        if (mediaPlayer.isPlaying) mediaPlayer.stop()
        mediaPlayer.release()
    }
}
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 背景渐变
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1A1A2E),
                            Color(0xFF16213E),
                            Color.Black
                        ),
                        radius = 800f
                    )
                )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 标题区域
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 40.dp)
            ) {
                Text(
                    text = "🎵",
                    fontSize = 48.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = "YOUR #1",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFFD700),
                    letterSpacing = 3.sp
                )
                
                Text(
                    text = "Song of the Year",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            // 主要内容卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2D2B55).copy(alpha = 0.8f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (topSong != null) {
                        // 1. 大缩略图显示区域
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (thumbnailUrl == null) Color(0xFF2A1B3D) else Color.Transparent
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (thumbnailUrl != null) {
                                // 使用 Coil 加载并显示网络图片
                                val painter = rememberAsyncImagePainter(
                                    model = ImageRequest.Builder(context)
                                        .data(thumbnailUrl)
                                        .crossfade(true)
                                        .build()
                                )
                                
                                if (painter.state is AsyncImagePainter.State.Loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(40.dp),
                                        strokeWidth = 3.dp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                } else {
                                    Image(
                                        painter = painter,
                                        contentDescription = "${topSong.song.title} thumbnail",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(20.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            } else if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    strokeWidth = 3.dp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            } else {
                                // 回退：显示音乐图标
                                Text(
                                    text = "🎵",
                                    fontSize = 60.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            
                            // 音频播放状态指示器
                            if (mediaPlayer.isPlaying) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFD700).copy(alpha = 0.8f))
                                        .border(2.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "♪",
                                        fontSize = 24.sp,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // 2. 歌曲信息
                        Text(
                            text = "\"${topSong.song.title}\"",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700),
                            textAlign = TextAlign.Center,
                            lineHeight = 32.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        
                        Text(
                            text = "by ${topSong.song.artistsText ?: "Unknown Artist"}",
                            fontSize = 18.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 32.dp, top = 8.dp)
                        )
                        
                        // 3. 统计卡片
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF3A3A6E).copy(alpha = 0.6f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "🔥 ${topSong.playCount}",
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFFF6B6B)
                                )
                                
                                Text(
                                    text = "total plays",
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 32.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "⏱️", fontSize = 20.sp, modifier = Modifier.padding(bottom = 4.dp))
                                        Text(
                                            text = "${topSong.minutes}",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "minutes",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                    
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "📅", fontSize = 20.sp, modifier = Modifier.padding(bottom = 4.dp))
                                        val avgPerDay = String.format("%.1f", topSong.playCount / 365.0)
                                        Text(
                                            text = avgPerDay,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "per day",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // 4. 音频控制按钮（可选）
                        if (previewUrl != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Button(
                                    onClick = {
                                        if (mediaPlayer.isPlaying) {
                                            mediaPlayer.pause()
                                        } else {
                                            if (!mediaPlayer.isPlaying) {
                                                mediaPlayer.start()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF9C27B0)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = if (mediaPlayer.isPlaying) "⏸️ Pause Preview" else "▶️ Play Preview",
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        // 5. 趣味事实卡片
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF9C27B0).copy(alpha = 0.2f))
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                    text = "✨ FUN FACT",
                                    fontSize = 12.sp,
                                    color = Color(0xFFE91E63),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                val totalHours = (topSong.minutes / 60f).roundToInt()
                                Text(
                                    text = "This was your most played song! You've listened ${topSong.playCount} times (${totalHours} hours).",
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.95f),
                                    lineHeight = 20.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // 无数据时的回退界面
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(text = "🎵", fontSize = 48.sp, modifier = Modifier.padding(bottom = 16.dp))
                            Text(
                                text = "No top song data available",
                                fontSize = 18.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            
            // 导航提示
            Box(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Swipe for top artists →",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// 获取歌曲信息的协程函数（来自你提供的代码）
private suspend fun fetchSongInfo(title: String, artist: String): Pair<String?, String?> {
    return withContext(Dispatchers.IO) {
        try {
            // 首先尝试 Deezer API
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val url = "https://api.deezer.com/search?q=$query&limit=1"
            
            val connection = URL(url).openConnection().apply {
                connectTimeout = 5000
                readTimeout = 5000
            }
            
            val response = connection.getInputStream().bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            
            if (json.has("data")) {
                val dataArray = json.getJSONArray("data")
                if (dataArray.length() > 0) {
                    val songData = dataArray.getJSONObject(0)
                    val thumbnail = songData.getJSONObject("album").getString("cover_big")
                    val preview = songData.optString("preview", null)
                    
                    return@withContext Pair(thumbnail, preview)
                }
            }
            
            // 回退方案：尝试 YouTube API 获取缩略图
            val ytQuery = URLEncoder.encode("$title $artist", "UTF-8")
            val ytUrl = "https://yt.omada.cafe/api/v1/search?q=$ytQuery&type=video"
            
            val ytConnection = URL(ytUrl).openConnection().apply {
                connectTimeout = 5000
                readTimeout = 5000
            }
            
            val ytResponse = ytConnection.getInputStream().bufferedReader().use { it.readText() }
            
            // 使用正则表达式解析 YouTube 响应中的缩略图URL
            val thumbnailMatch = Regex("\"videoThumbnails\":\\s*\\[.*?\"url\":\"(.*?)\"").find(ytResponse)
            val thumbnail = thumbnailMatch?.groups?.get(1)?.value?.replace("\\/", "/")
            
            return@withContext Pair(thumbnail, null)
            
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Pair(null, null)
        }
    }
}