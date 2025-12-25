package it.fast4x.rimusic.ui.screens.rewind.slides

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.fast4x.rimusic.ui.screens.rewind.*
import it.fast4x.rimusic.ui.screens.rewind.components.hourDescriptions
import androidx.compose.foundation.background

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