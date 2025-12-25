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
import it.fast4x.rimusic.ui.screens.rewind.components.dayNames
import androidx.compose.foundation.background

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