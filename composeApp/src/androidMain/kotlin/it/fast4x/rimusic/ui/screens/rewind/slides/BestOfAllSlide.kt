package it.fast4x.rimusic.ui.screens.rewind.slides

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.fast4x.rimusic.ui.screens.rewind.*
import java.time.LocalDate
import android.content.Intent
import androidx.compose.ui.platform.LocalUriHandler
import java.net.URLEncoder

@Composable
fun BestOfAllSlide(data: RewindData, onNext: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val year = LocalDate.now().year
    
    val topSong = data.topSongs.firstOrNull()
    val topArtist = data.topArtists.firstOrNull()
    val topAlbum = data.topAlbums.firstOrNull()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "🌌 COSMIC REWIND $year",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "Your Top Musical Highlights",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 30.dp)
        )
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Song
            CosmicItemCard(
                icon = "🎵",
                title = "TOP SONG",
                mainText = topSong?.song?.title ?: "N/A",
                subText = topSong?.song?.artistsText ?: "",
                stats = "${topSong?.playCount ?: 0} plays • ${topSong?.minutes ?: 0} min"
            )
            
            // Top Artist
            CosmicItemCard(
                icon = "⭐",
                title = "TOP ARTIST",
                mainText = topArtist?.artist?.name ?: "N/A",
                subText = "${topArtist?.songCount ?: 0} songs",
                stats = "${topArtist?.minutes ?: 0} minutes"
            )
            
            // Top Album
            CosmicItemCard(
                icon = "💿",
                title = "TOP ALBUM",
                mainText = topAlbum?.album?.title ?: "N/A",
                subText = topAlbum?.album?.authorsText ?: "",
                stats = "${topAlbum?.songCount ?: 0} songs • ${topAlbum?.minutes ?: 0} min"
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Description Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0x152A1F3A)
            ),
            border = BorderStroke(1.dp, Color(0x30D0BCFF))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Magic icon
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFD0BCFF),
                                    Color(0xFF6750A4)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✨",
                        fontSize = 24.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "View Your Complete Rewind",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Experience your music journey with beautiful visualizations, " +
                           "interactive charts, and detailed insights on our website.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Share Button
        Card(
            onClick = {
                // Encode data for URL
                val dataString = encodeTopDataForUrl(
                    topSong = topSong,
                    topArtist = topArtist,
                    topAlbum = topAlbum,
                    year = year,
                    totalMinutes = data.stats.totalMinutes,
                    totalPlays = data.stats.totalPlays
                )
                val url = "https://cubicrewind.lovable.app?data=$dataString"
                uriHandler.openUri(url)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            border = BorderStroke(
                2.dp,
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFD0BCFF),
                        Color(0xFF6750A4),
                        Color(0xFFD0BCFF)
                    )
                )
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp,
                pressedElevation = 4.dp
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0x30D0BCFF),
                                Color(0x106750A4),
                                Color(0x30D0BCFF)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFD0BCFF))
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🌠",
                            fontSize = 18.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "View Cosmic Rewind",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Interactive visualizations on web",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF6750A4).copy(alpha = 0.5f))
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "↗",
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Alternative share button
        Card(
            onClick = {
                shareTopData(
                    context = context,
                    topSong = topSong,
                    topArtist = topArtist,
                    topAlbum = topAlbum,
                    year = year,
                    totalMinutes = data.stats.totalMinutes,
                    totalPlays = data.stats.totalPlays
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0x156750A4)
            ),
            border = BorderStroke(1.dp, Color(0x30D0BCFF))
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📤",
                    fontSize = 18.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "Share My Top Picks",
                    fontSize = 14.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "✨ Powered by BlackCherryCosmos Shader",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun CosmicItemCard(
    icon: String,
    title: String,
    mainText: String,
    subText: String,
    stats: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x152A1F3A)
        ),
        border = BorderStroke(1.dp, Color(0x30D0BCFF)),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon with cosmic effect
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1E1B26),
                                Color(0xFF2A1F3A)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon,
                    fontSize = 24.sp
                )
            }
            
            // Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                Text(
                    text = mainText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
                
                if (subText.isNotEmpty()) {
                    Text(
                        text = subText,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = stats,
                    fontSize = 11.sp,
                    color = Color(0xFFD0BCFF),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// Helper function to encode data for URL
private fun encodeTopDataForUrl(
    topSong: TopSong?,
    topArtist: TopArtist?,
    topAlbum: TopAlbum?,
    year: Int,
    totalMinutes: Long,
    totalPlays: Int
): String {
    return try {
        val jsonString = buildString {
            append("{")
            append("\"year\":$year,")
            append("\"totalMinutes\":$totalMinutes,")
            append("\"totalPlays\":$totalPlays,")
            
            if (topSong != null) {
                append("\"topSong\":{")
                append("\"title\":\"${escapeJsonString(topSong.song.title)}\",")
                append("\"artist\":\"${escapeJsonString(topSong.song.artistsText ?: "")}\",")
                append("\"plays\":${topSong.playCount},")
                append("\"minutes\":${topSong.minutes}")
                append("},")
            }
            
            if (topArtist != null) {
                append("\"topArtist\":{")
                append("\"name\":\"${escapeJsonString(topArtist.artist.name ?: "")}\",")
                append("\"minutes\":${topArtist.minutes},")
                append("\"songCount\":${topArtist.songCount}")
                append("},")
            }
            
            if (topAlbum != null) {
                append("\"topAlbum\":{")
                append("\"title\":\"${escapeJsonString(topAlbum.album.title ?: "")}\",")
                append("\"artist\":\"${escapeJsonString(topAlbum.album.authorsText ?: "")}\",")
                append("\"minutes\":${topAlbum.minutes},")
                append("\"songCount\":${topAlbum.songCount}")
                append("}")
            }
            
            // Remove trailing comma
            if (toString().endsWith(",")) {
                deleteCharAt(length - 1)
            }
            
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

private fun shareTopData(
    context: android.content.Context,
    topSong: TopSong?,
    topArtist: TopArtist?,
    topAlbum: TopAlbum?,
    year: Int,
    totalMinutes: Long,
    totalPlays: Int
) {
    val shareText = buildString {
        append("🌌 My $year Top Music Picks 🌌\n\n")
        append("📊 STATS:\n")
        append("• Total Listening: $totalMinutes minutes\n")
        append("• Total Plays: $totalPlays\n\n")
        
        append("🏆 TOP PICKS:\n")
        
        if (topSong != null) {
            append("🎵 Top Song: ${topSong.song.title} - ${topSong.song.artistsText ?: "Unknown Artist"}\n")
            append("   ${topSong.playCount} plays • ${topSong.minutes} minutes\n\n")
        }
        
        if (topArtist != null) {
            append("⭐ Top Artist: ${topArtist.artist.name ?: "Unknown Artist"}\n")
            append("   ${topArtist.songCount} songs • ${topArtist.minutes} minutes\n\n")
        }
        
        if (topAlbum != null) {
            append("💿 Top Album: ${topAlbum.album.title ?: "Unknown Album"}\n")
            append("   ${topAlbum.album.authorsText ?: "Unknown Artist"}\n")
            append("   ${topAlbum.songCount} songs • ${topAlbum.minutes} minutes\n\n")
        }
        
        append("🔗 View my complete cosmic rewind with interactive visualizations at:")
        append("\nhttps://cubicrewind.lovable.app")
        append("\n\n✨ Powered by BlackCherryCosmos ✨")
    }
    
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
        putExtra(Intent.EXTRA_SUBJECT, "My $year Top Music Picks")
    }
    
    context.startActivity(Intent.createChooser(shareIntent, "Share Your Top Picks"))
}