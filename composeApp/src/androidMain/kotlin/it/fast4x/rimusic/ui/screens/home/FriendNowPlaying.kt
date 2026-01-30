package it.fast4x.rimusic.ui.screens.home

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import app.kreate.android.R
import androidx.navigation.NavController
import com.mikepenz.hypnoticcanvas.shaderBackground
import com.mikepenz.hypnoticcanvas.shaders.BlackCherryCosmos
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.utils.forcePlay
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.models.Song

// ===== IMPORT THE CUBIC JAM HELPER FUNCTIONS =====
import it.fast4x.rimusic.ui.screens.cubicjam.refreshFriendsActivity

// ===== DATA CLASSES =====
data class FriendNowPlaying(
    val friendName: String,
    val trackTitle: String,
    val artistName: String,
    val albumArtUrl: String?,
    val isPlaying: Boolean = true,
    val id: String = "",
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val isOnline: Boolean = true
)

data class FriendStatus(
    val friendName: String,
    val isOnline: Boolean,
    val isPlaying: Boolean,
    val lastActivity: FriendNowPlaying? = null
)

data class NowPlayingState(
    val friendNowPlaying: FriendNowPlaying? = null,
    val friendStatus: FriendStatus? = null,
    val isDismissed: Boolean = false,
    val swipeOffset: Float = 0f,
    val isLoading: Boolean = false,
    val lastUpdateTime: Long = 0
)

// ===== HELPER FUNCTIONS =====
@Composable
fun rememberNowPlayingState(): NowPlayingStateHolder {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val json = remember { kotlinx.serialization.json.Json { ignoreUnknownKeys = true } }
    
    val state = remember { 
        mutableStateOf(NowPlayingState()) 
    }
    
    val isCubicJamLoggedIn = remember {
        val prefs = context.getSharedPreferences("cubic_jam_prefs", Context.MODE_PRIVATE)
        val token = prefs.getString("bearer_token", null)
        val isLoggedIn = token != null
        
        Timber.d("CubicJam - Is logged in: $isLoggedIn, Token exists: ${token != null}")
        isLoggedIn
    }
    
    // Function to fetch friend activity
    fun fetchFriendNowPlaying() {
        if (!isCubicJamLoggedIn || state.value.isLoading) return
        
        scope.launch {
            try {
                state.value = state.value.copy(isLoading = true)
                
                val prefs = context.getSharedPreferences("cubic_jam_prefs", Context.MODE_PRIVATE)
                val result = refreshFriendsActivity(prefs, json)
                
                Timber.d("CubicJam - Fetch result: ${result != null}")
                
                if (result != null && result.activity.isNotEmpty()) {
                    val currentUsername = prefs.getString("username", null)
                    val currentTime = System.currentTimeMillis()
                    
                    // Find first online friend (playing or not)
                    var activeFriend: it.fast4x.rimusic.ui.screens.cubicjam.FriendActivityResponseItem? = null
                    var onlineFriend: it.fast4x.rimusic.ui.screens.cubicjam.FriendActivityResponseItem? = null
                    
                    for (activityItem in result.activity) {
                        val username = activityItem.profile.username
                        val isOnline = activityItem.is_online
                        
                        Timber.d("CubicJam - Checking friend: $username, online: $isOnline")
                        
                        // Skip current user
                        if (username == currentUsername) continue
                        
                        // First check for online and playing friend
                        if (isOnline && activityItem.is_playing && activityItem.track_id.isNotEmpty()) {
                            activeFriend = activityItem
                            Timber.d("CubicJam - Found active playing friend: $username")
                            break
                        }
                        
                        // If no playing friend found yet, track any online friend
                        if (isOnline && onlineFriend == null) {
                            onlineFriend = activityItem
                            Timber.d("CubicJam - Found online friend: $username")
                        }
                    }
                    
                    // Priority: 1. Playing friend, 2. Online friend, 3. Nothing
                    val targetFriend = activeFriend ?: onlineFriend
                    
                    if (targetFriend != null) {
                        val displayName = targetFriend.profile.display_name ?: targetFriend.profile.username
                        
                        if (targetFriend.is_playing && targetFriend.track_id.isNotEmpty()) {
                            // Friend is playing
                            val friendNowPlaying = FriendNowPlaying(
                                friendName = displayName,
                                trackTitle = targetFriend.title,
                                artistName = targetFriend.artist,
                                albumArtUrl = targetFriend.artwork_url,
                                isPlaying = targetFriend.is_playing,
                                id = targetFriend.track_id,
                                durationMs = targetFriend.duration_ms,
                                positionMs = targetFriend.position_ms,
                                isOnline = targetFriend.is_online
                            )
                            
                            Timber.d("CubicJam - Created playing friend: ${friendNowPlaying.friendName}")
                            
                            state.value = NowPlayingState(
                                friendNowPlaying = friendNowPlaying,
                                friendStatus = FriendStatus(
                                    friendName = displayName,
                                    isOnline = true,
                                    isPlaying = true,
                                    lastActivity = friendNowPlaying
                                ),
                                isDismissed = state.value.isDismissed,
                                swipeOffset = 0f,
                                isLoading = false,
                                lastUpdateTime = currentTime
                            )
                        } else {
                            // Friend is online but not playing
                            val friendStatus = FriendStatus(
                                friendName = displayName,
                                isOnline = true,
                                isPlaying = false,
                                lastActivity = state.value.friendNowPlaying
                            )
                            
                            Timber.d("CubicJam - Friend online but not playing: $displayName")
                            
                            state.value = NowPlayingState(
                                friendNowPlaying = state.value.friendNowPlaying, // Keep last playing info
                                friendStatus = friendStatus,
                                isDismissed = state.value.isDismissed,
                                swipeOffset = 0f,
                                isLoading = false,
                                lastUpdateTime = currentTime
                            )
                        }
                    } else {
                        Timber.d("CubicJam - No online friends found")
                        // Keep last state but mark as no online friends
                        state.value = state.value.copy(
                            isLoading = false,
                            lastUpdateTime = currentTime
                        )
                    }
                } else {
                    Timber.d("CubicJam - No activity data in response")
                    state.value = state.value.copy(
                        isLoading = false,
                        lastUpdateTime = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                Timber.e("CubicJam - Error fetching friend now playing: ${e.message}")
                state.value = state.value.copy(isLoading = false)
            }
        }
    }
    
    // Start periodic updates
    LaunchedEffect(isCubicJamLoggedIn) {
        if (isCubicJamLoggedIn) {
            Timber.d("CubicJam - Starting periodic updates")
            while (true) {
                fetchFriendNowPlaying()
                delay(15000) // Update every 15 seconds
            }
        } else {
            Timber.d("CubicJam - Not logged in, skipping periodic updates")
        }
    }
    
    // Initial fetch
    LaunchedEffect(isCubicJamLoggedIn) {
        if (isCubicJamLoggedIn) {
            Timber.d("CubicJam - Initial fetch")
            fetchFriendNowPlaying()
        }
    }
    
    return NowPlayingStateHolder(
        state = state.value,
        fetchFriendNowPlaying = { fetchFriendNowPlaying() },
        updateState = { newState ->
            state.value = newState
        }
    )
}

data class NowPlayingStateHolder(
    val state: NowPlayingState,
    val fetchFriendNowPlaying: () -> Unit,
    val updateState: (NowPlayingState) -> Unit
)

// ===== COMPONENT =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendNowPlayingSection(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val binder = LocalPlayerServiceBinder.current
    val nowPlayingStateHolder = rememberNowPlayingState()
    val state = nowPlayingStateHolder.state
    
    Timber.d("CubicJam - Component state: friend=${state.friendNowPlaying != null}, status=${state.friendStatus}, dismissed=${state.isDismissed}")
    
    // Show if we have friend status and not dismissed
    if (!state.isLoading && state.friendStatus != null && !state.isDismissed) {
        Timber.d("CubicJam - Showing friend status: ${state.friendStatus.friendName}")
        DismissibleFriendNowPlaying(
            state = state,
            onDismiss = {
                Timber.d("CubicJam - Dismissing friend")
                nowPlayingStateHolder.updateState(state.copy(isDismissed = true))
            },
            onPlayClick = { friendNowPlaying ->
                Timber.d("CubicJam - Playing friend's song: ${friendNowPlaying.trackTitle}")
                binder?.let {
                    val song = Song(
                        id = friendNowPlaying.id,
                        title = friendNowPlaying.trackTitle,
                        artistsText = friendNowPlaying.artistName,
                        durationText = "",
                        thumbnailUrl = friendNowPlaying.albumArtUrl,
                        likedAt = null
                    )
                    it.stopRadio()
                    it.player?.forcePlay(song.asMediaItem)
                }
            },
            onRefresh = {
                Timber.d("CubicJam - Manual refresh")
                nowPlayingStateHolder.fetchFriendNowPlaying()
            }
        )
    } else {
        Timber.d("CubicJam - Not showing (isLoading=${state.isLoading}, status=${state.friendStatus}, dismissed=${state.isDismissed})")
    }
}

@Composable
private fun GlowingDot(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    Box(
        modifier = Modifier
            .size(16.dp)
            .drawWithContent {
                // Draw outer glow
                drawCircle(
                    color = if (isPlaying) Color.Green else Color.White.copy(alpha = 0.5f),
                    radius = size.minDimension / 2 * pulseScale,
                    alpha = glowAlpha * 0.3f,
                    style = Stroke(width = 2.dp.toPx())
                )
                
                // Draw inner circle
                drawCircle(
                    color = if (isPlaying) Color.Green else Color.White.copy(alpha = 0.8f),
                    radius = size.minDimension / 3,
                    alpha = if (isPlaying) glowAlpha else 0.8f
                )
            }
    )
}

@Composable
private fun DismissibleFriendNowPlaying(
    state: NowPlayingState,
    onDismiss: () -> Unit,
    onPlayClick: (FriendNowPlaying) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val friendStatus = state.friendStatus ?: return
    val friendNowPlaying = state.friendNowPlaying
    var swipeOffset by remember { mutableStateOf(state.swipeOffset) }
    
    val maxSwipeDistance = 300f
    val dismissThreshold = maxSwipeDistance * 0.6f
    
    // Use BlackCherryCosmos shader for the background
    val selectedShader = remember { BlackCherryCosmos }
    
    Timber.d("CubicJam - Rendering DismissibleFriendNowPlaying for: ${friendStatus.friendName}")
    
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .offset { IntOffset(swipeOffset.roundToInt(), 0) }
                .alpha(1f - (abs(swipeOffset) / dismissThreshold).coerceIn(0f, 1f))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(swipeOffset) > dismissThreshold) {
                                onDismiss()
                            } else {
                                swipeOffset = 0f
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            val newOffset = swipeOffset + dragAmount
                            swipeOffset = newOffset.coerceIn(-maxSwipeDistance, maxSwipeDistance)
                            change.consume()
                        }
                    )
                }
        ) {
            // Main container with BlackCherryCosmos shader
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .shaderBackground(selectedShader)
            ) {
                // Dark overlay for better text readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                )
                
                // Content
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Album Art
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (friendNowPlaying?.albumArtUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(friendNowPlaying.albumArtUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "${friendNowPlaying.trackTitle} album art",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.music),
                                contentDescription = "No album art",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        
                        // Online/Playing indicator in top-right
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                        ) {
                            GlowingDot(isPlaying = friendStatus.isPlaying && friendStatus.isOnline)
                        }
                    }
                    
                    // Song Info
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Friend name and status
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = friendStatus.friendName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.9f)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            Text(
                                text = when {
                                    friendStatus.isPlaying && friendStatus.isOnline -> "🫠 vibing"
                                    friendStatus.isOnline -> "😎 online"
                                    else -> "🥺 offline"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = when {
                                        friendStatus.isPlaying && friendStatus.isOnline -> Color.Green.copy(alpha = 0.9f)
                                        friendStatus.isOnline -> Color.Cyan.copy(alpha = 0.8f)
                                        else -> Color.White.copy(alpha = 0.5f)
                                    }
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        if (friendNowPlaying != null && friendStatus.isPlaying) {
                            // Show song info when friend is playing
                            Text(
                                text = friendNowPlaying.trackTitle,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            Text(
                                text = friendNowPlaying.artistName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.9f)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            // Progress indicator (shows progress if paused, otherwise shows "vibing")
                            if (friendNowPlaying.durationMs > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .background(Color.White.copy(alpha = 0.2f))
                                        .clip(RoundedCornerShape(2.dp))
                                ) {
                                    if (friendNowPlaying.isPlaying) {
                                        // Show animated "vibing" effect
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight()
                                                .background(
                                                    brush = Brush.linearGradient(
                                                        colors = listOf(
                                                            Color.Green.copy(alpha = 0.7f),
                                                            Color.Cyan.copy(alpha = 0.7f),
                                                            Color.Green.copy(alpha = 0.7f)
                                                        )
                                                    )
                                                )
                                        )
                                    } else {
                                        // Show static progress bar at paused position
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(
                                                    fraction = friendNowPlaying.positionMs.toFloat() / 
                                                              friendNowPlaying.durationMs.toFloat()
                                                )
                                                .fillMaxHeight()
                                                .background(Color.White.copy(alpha = 0.8f))
                                        )
                                    }
                                }
                            }
                        } else if (friendNowPlaying != null) {
                            // Friend was recently playing but is now paused/offline
                            Text(
                                text = "Last played: ${friendNowPlaying.trackTitle}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.7f)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            // Friend is online but not playing anything recently
                            Text(
                                text = "Online",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.7f)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    // Action buttons
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (friendNowPlaying != null && friendStatus.isPlaying) {
                            // Play Button (only show if friend is playing)
                            IconButton(
                                onClick = { onPlayClick(friendNowPlaying) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.play),
                                    contentDescription = "Play this song",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        // Dismiss Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                // Swipe hint - Use arrow icon if chevron not available
                if (abs(swipeOffset) < 10f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp)
                            .alpha(0.5f)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_right),
                            contentDescription = "Swipe hint",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        
        // Refresh button (outside the swipeable box)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(
                    painter = painterResource(R.drawable.refresh),
                    contentDescription = "Refresh friend activity",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}