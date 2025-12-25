package it.fast4x.rimusic.ui.screens.rewind.slides

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.fast4x.rimusic.ui.screens.rewind.RewindData
import it.fast4x.rimusic.ui.screens.rewind.components.StatItem
import it.fast4x.rimusic.ui.screens.rewind.components.StatRow

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
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header with better styling
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📊",
                fontSize = 40.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "YOUR YEAR IN NUMBERS",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp
            )
        }
        
        // Ranking Card with improved design
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 20.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🏆",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        text = "YOUR RANKING",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = userRanking,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = userPercentile,
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Normal
                )
            }
        }
        
        // Main Stats Grid with improved spacing
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            // First row of main stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                        if (minutes > 0) "$hours h $minutes m" else "$hours h"
                    } else {
                        "${data.stats.totalMinutes} m"
                    },
                    label = if (showMinutesInHours) "Listening Time" else "Minutes",
                    icon = "⏱️",
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Second row of main stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
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
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Additional Stats Section
        Text(
            text = "More Insights",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.08f)
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 4.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Update StatRow calls to remove the modifier parameter if it doesn't exist
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "👥",
                            fontSize = 18.sp,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            text = "Unique Artists",
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = data.totalUniqueArtists.toString(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                Divider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = Color.White.copy(alpha = 0.1f),
                    thickness = 1.dp
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💿",
                            fontSize = 18.sp,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            text = "Unique Albums",
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = data.totalUniqueAlbums.toString(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                Divider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = Color.White.copy(alpha = 0.1f),
                    thickness = 1.dp
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📋",
                            fontSize = 18.sp,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            text = "Unique Playlists",
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = data.totalUniquePlaylists.toString(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Navigation hint with better styling
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "➡️",
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "Swipe for Top Songs",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}