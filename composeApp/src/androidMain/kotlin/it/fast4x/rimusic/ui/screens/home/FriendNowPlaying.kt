package it.fast4x.rimusic.ui.screens.home

import android.content.Context
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.graphics.Color
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
import it.fast4x.rimusic.ui.screens.cubicjam.refreshFriendsActivity // This is the key import!

// ===== DATA CLASSES =====
data class FriendNowPlaying(
    val friendName: String,
    val trackTitle: String,
    val artistName: String,
    val albumArtUrl: String?,
    val isPlaying: Boolean = true,
    val id: String = "",
    val durationMs: Long = 0,
    val positionMs: Long = 0
)

data class NowPlayingState(
    val friendNowPlaying: FriendNowPlaying? = null,
    val isDismissed: Boolean = false,
    val swipeOffset: Float = 0f,
    val isLoading: Boolean = false
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
    
    // Function to fetch friend activity - USING THE CORRECT API ENDPOINT
    fun fetchFriendNowPlaying() {
        if (!isCubicJamLoggedIn || state.value.isLoading) return
        
        scope.launch {
            try {
                state.value = state.value.copy(isLoading = true)
                
                val prefs = context.getSharedPreferences("cubic_jam_prefs", Context.MODE_PRIVATE)
                
                // USE THE CORRECT HELPER FUNCTION FROM CUBICJAM PACKAGE
                val result = refreshFriendsActivity(prefs, json)
                
                Timber.d("CubicJam - Fetch result: ${result != null}")
                
                if (result != null && result.activity.isNotEmpty()) {
                    val currentUsername = prefs.getString("username", null)
                    
                    var activeFriend: it.fast4x.rimusic.ui.screens.cubicjam.FriendActivityResponseItem? = null
                    for (activityItem in result.activity) {
                        val username = activityItem.profile.username
                        val isOnline = activityItem.is_online
                        val isPlaying = activityItem.is_playing
                        val hasTrackId = activityItem.track_id.isNotEmpty()
                        
                        Timber.d("CubicJam - Checking friend: $username, online: $isOnline, playing: $isPlaying, hasTrackId: $hasTrackId")
                        
                        if (username != currentUsername && isOnline && isPlaying && hasTrackId) {
                            activeFriend = activityItem
                            Timber.d("CubicJam - Found active friend: $username")
                            break
                        }
                    }
                    
                    if (activeFriend != null) {
                        val displayName = activeFriend.profile.display_name ?: activeFriend.profile.username
                        
                        val friendNowPlaying = FriendNowPlaying(
                            friendName = displayName,
                            trackTitle = activeFriend.title,
                            artistName = activeFriend.artist,
                            albumArtUrl = activeFriend.artwork_url,
                            isPlaying = activeFriend.is_playing,
                            id = activeFriend.track_id,
                            durationMs = activeFriend.duration_ms,
                            positionMs = activeFriend.position_ms
                        )
                        
                        Timber.d("CubicJam - Created FriendNowPlaying: ${friendNowPlaying.friendName}, ${friendNowPlaying.trackTitle}")
                        
                        if (state.value.friendNowPlaying?.id != friendNowPlaying.id || 
                            state.value.isDismissed) {
                            state.value = NowPlayingState(
                                friendNowPlaying = friendNowPlaying,
                                isDismissed = false,
                                swipeOffset = 0f,
                                isLoading = false
                            )
                            Timber.d("CubicJam - Updated state with new friend")
                        } else {
                            Timber.d("CubicJam - Same friend or dismissed, not updating")
                        }
                    } else {
                        Timber.d("CubicJam - No active friends found")
                        state.value = state.value.copy(
                            friendNowPlaying = null,
                            isLoading = false
                        )
                    }
                } else {
                    Timber.d("CubicJam - No activity data in response or empty response")
                    state.value = state.value.copy(
                        friendNowPlaying = null,
                        isLoading = false
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
    
    Timber.d("CubicJam - Component state: isLoading=${state.isLoading}, friend=${state.friendNowPlaying != null}, dismissed=${state.isDismissed}")
    
    if (!state.isLoading && state.friendNowPlaying != null && !state.isDismissed) {
        Timber.d("CubicJam - Showing friend: ${state.friendNowPlaying.friendName}")
        DismissibleFriendNowPlaying(
            state = state,
            onDismiss = {
                Timber.d("CubicJam - Dismissing friend")
                nowPlayingStateHolder.updateState(state.copy(isDismissed = true))
            },
            onPlayClick = { friendNowPlaying ->
                Timber.d("CubicJam - Playing friend's song: ${friendNowPlaying.trackTitle}")
                // Play the friend's song using the binder like HomeQuickPicks does
                binder?.let {
                    // Create a simple song object with the video ID
                    val song = Song(
                        id = friendNowPlaying.id,
                        title = friendNowPlaying.trackTitle,
                        artistsText = friendNowPlaying.artistName,
                        durationText = "",
                        thumbnailUrl = friendNowPlaying.albumArtUrl,
                        likedAt = null
                    )
                    
                    // Play the song exactly like HomeQuickPicks does
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
        Timber.d("CubicJam - Not showing (isLoading=${state.isLoading}, friend=${state.friendNowPlaying != null}, dismissed=${state.isDismissed})")
    }
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
    val friendNowPlaying = state.friendNowPlaying ?: return
    var swipeOffset by remember { mutableStateOf(state.swipeOffset) }
    
    val maxSwipeDistance = 300f
    val dismissThreshold = maxSwipeDistance * 0.6f
    
    // Use BlackCherryCosmos shader for the background
    val selectedShader = remember { BlackCherryCosmos }
    
    Timber.d("CubicJam - Rendering DismissibleFriendNowPlaying for: ${friendNowPlaying.friendName}")
    
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
                        .background(Color.Black.copy(alpha = 0.15f))
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
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        friendNowPlaying.albumArtUrl?.let { url ->
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(url)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "${friendNowPlaying.trackTitle} album art",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } ?: run {
                            Icon(
                                painter = painterResource(R.drawable.music),
                                contentDescription = "No album art",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        
                        // Play/Pause indicator
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    if (friendNowPlaying.isPlaying) 
                                        Color.Green.copy(alpha = 0.9f)
                                    else 
                                        Color.White.copy(alpha = 0.3f)
                                )
                                .align(Alignment.BottomEnd)
                                .padding(3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (friendNowPlaying.isPlaying) 
                                        R.drawable.pause 
                                    else 
                                        R.drawable.play
                                ),
                                contentDescription = if (friendNowPlaying.isPlaying) "Playing" else "Paused",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                    
                    // Song Info
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "${friendNowPlaying.friendName} is listening",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White.copy(alpha = 0.7f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Text(
                            text = friendNowPlaying.trackTitle,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Text(
                            text = friendNowPlaying.artistName,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.8f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        // Progress bar (optional)
                        if (friendNowPlaying.durationMs > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .clip(RoundedCornerShape(1.dp))
                            ) {
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
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Play Button
                        IconButton(
                            onClick = { onPlayClick(friendNowPlaying) },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.play),
                                contentDescription = "Play this song",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
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
                        // Try to use available icon
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
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
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