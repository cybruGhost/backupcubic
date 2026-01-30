package it.fast4x.rimusic.ui.screens.cubicjam

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.kreate.android.R
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlinx.serialization.json.Json

@Composable
fun CubicJamScreen(
    navController: NavController,
    cubicJamManager: CubicJamManager? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true } }
    
    // Load from SharedPreferences
    val preferences = remember { 
        context.getSharedPreferences("cubic_jam_prefs", Context.MODE_PRIVATE) 
    }
    
    // State variables - FIXED: Using mutableStateOf correctly
    var isLoggedIn by remember { 
        mutableStateOf(preferences.getString("bearer_token", null) != null) 
    }
    var username by remember { 
        mutableStateOf(preferences.getString("username", null)) 
    }
    var displayName by remember { 
        mutableStateOf(preferences.getString("display_name", null)) 
    }
    var friendCode by remember { 
        mutableStateOf(preferences.getString("friend_code", null)) 
    }
    
    // Friends state
    var friendsActivity by remember { mutableStateOf<List<FriendActivity>>(emptyList()) }
    var selectedFriendIndex by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshJob by remember { mutableStateOf<Job?>(null) }
    
    // Colors
    val primaryColor = remember { Color(0xFF7C4DFF) }
    val secondaryColor = remember { Color(0xFFFF9800) }
    val backgroundColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val onlineColor = Color(0xFF4CAF50)
    val offlineColor = Color(0xFF757575)
    
    // Function to fetch friends - FIXED
    suspend fun fetchFriends() {
        if (!isLoggedIn) {
            errorMessage = "Not logged in"
            return
        }
        
        try {
            isLoading = true
            errorMessage = null
            
            Timber.tag("CubicJam").d("fetchFriends: Starting fetch...")
            
            val result = refreshFriendsActivity(preferences, json)
            if (result != null) {
                // Get the activity list from the API response
                val activities = result.activity
                
                // Convert to FriendActivity objects
                val friendActivities = activities.map { activityItem ->
                    FriendActivity(
                        profile = activityItem.profile,
                        activity = Activity(
                            track_id = activityItem.track_id,
                            title = activityItem.title,
                            artist = activityItem.artist,
                            album = activityItem.album,
                            artwork_url = activityItem.artwork_url,
                            is_playing = activityItem.is_playing,
                            position_ms = activityItem.position_ms,
                            duration_ms = activityItem.duration_ms,
                            updated_at = activityItem.updated_at
                        ),
                        is_online = activityItem.is_online
                    )
                }
                
                // Filter out current user if we have username
                val filteredFriends = username?.let { currentUsername ->
                    friendActivities.filter { 
                        it.profile.username != currentUsername 
                    }
                } ?: friendActivities
                
                friendsActivity = filteredFriends
                
                if (filteredFriends.isEmpty()) {
                    errorMessage = "No friends found. Add friends to see their activity!"
                } else {
                    errorMessage = null
                    Timber.tag("CubicJam").d("fetchFriends: Found ${filteredFriends.size} friends")
                }
            } else {
                errorMessage = "Failed to load friends. Check your connection."
                Timber.tag("CubicJam").e("fetchFriends: Result is null")
            }
        } catch (e: Exception) {
            Timber.tag("CubicJam").e(e, "fetchFriends: Failed completely")
            errorMessage = "Error: ${e.message}"
            friendsActivity = emptyList()
        } finally {
            isLoading = false
        }
    }
    
    // Initial fetch
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            Timber.tag("CubicJam").d("User is logged in, starting fetch...")
            
            // Stop any existing refresh job
            refreshJob?.cancel()
            
            // Fetch friends
            fetchFriends()
            
            // Start periodic refresh - FIXED: no while(true) loop
            refreshJob = scope.launch {
                while (true) {
                    delay(10000)
                    if (isLoggedIn) {
                        fetchFriends()
                    } else {
                        break
                    }
                }
            }
        }
    }
    
    // Cleanup on composition end
    DisposableEffect(Unit) {
        onDispose {
            refreshJob?.cancel()
            refreshJob = null
        }
    }
    
    // If not logged in, show auth screen
    if (!isLoggedIn) {
        CubicJamAuth(navController = navController)
        return
    }
    
    val selectedFriend = if (friendsActivity.isNotEmpty()) {
        friendsActivity.getOrNull(selectedFriendIndex)
    } else {
        null
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        // Top user info section
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                displayName?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                    )
                }
                
                username?.let { name ->
                    Text(
                        text = "@$name",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                
                friendCode?.let { code ->
                    Text(
                        text = "Code: $code",
                        style = MaterialTheme.typography.bodySmall,
                        color = primaryColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            
            // Profile button
            IconButton(
                onClick = {
                    username?.let { name ->
                        navController.navigate("cubicjam_web?url=https://jam-wave-connect.lovable.app/profile/$name")
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(primaryColor.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    painter = painterResource(R.drawable.person),
                    contentDescription = "View Profile",
                    tint = primaryColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Error message
        errorMessage?.let { message ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color.Red.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.alert_circle),
                        contentDescription = "Error",
                        tint = Color.Red,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Red,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { errorMessage = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = "Dismiss",
                            tint = Color.Red,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Main content area
        when {
            isLoading && friendsActivity.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.7f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = primaryColor)
                        Text(
                            text = "Loading friends...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            friendsActivity.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.7f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.people),
                            contentDescription = "No Friends",
                            tint = primaryColor.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "No Friends Yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = primaryColor.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "Add friends to see what they're listening to",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = { navController.navigate("cubicjam_add_friend") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryColor,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Add Friend")
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.8f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        selectedFriend?.let { friend ->
                            // Friend info header
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Profile picture
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(CircleShape)
                                            .background(primaryColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = friend.profile.username.take(1).uppercase(),
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        )
                                    }
                                    
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = friend.profile.display_name ?: friend.profile.username,
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = textColor
                                        )
                                        
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (friend.is_online) onlineColor else offlineColor
                                                    )
                                            )
                                            Text(
                                                text = if (friend.is_online) "Online" else "Offline",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (friend.is_online) onlineColor else offlineColor
                                            )
                                        }
                                    }
                                    
                                    // View profile button
                                    IconButton(
                                        onClick = { 
                                            navController.navigate("cubicjam_web?url=https://jam-wave-connect.lovable.app/profile/${friend.profile.username}")
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(secondaryColor.copy(alpha = 0.1f), CircleShape)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.globe),
                                            contentDescription = "View Profile",
                                            tint = secondaryColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                
                                // Now playing section
                                friend.activity?.let { activity ->
                                    val surfaceColor = primaryColor.copy(alpha = 0.1f)
                                    val friendName = friend.profile.display_name ?: friend.profile.username
                                    
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(20.dp),
                                        color = surfaceColor,
                                        border = BorderStroke(2.dp, primaryColor.copy(alpha = 0.3f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(20.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            // Album art (big)
                                            activity.artwork_url?.let { url ->
                                                SubcomposeAsyncImage(
                                                    model = url,
                                                    contentDescription = "Album Art",
                                                    modifier = Modifier
                                                        .size(200.dp)
                                                        .clip(RoundedCornerShape(12.dp)),
                                                    contentScale = ContentScale.Crop,
                                                    loading = {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(200.dp)
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(primaryColor),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                painter = painterResource(R.drawable.music),
                                                                contentDescription = "Loading",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(48.dp)
                                                            )
                                                        }
                                                    },
                                                    error = {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(200.dp)
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(primaryColor),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                painter = painterResource(R.drawable.music),
                                                                contentDescription = "No Album Art",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(48.dp)
                                                            )
                                                        }
                                                    }
                                                )
                                            } ?: run {
                                                Box(
                                                    modifier = Modifier
                                                        .size(200.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(primaryColor),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.music),
                                                        contentDescription = "No Album Art",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(64.dp)
                                                    )
                                                }
                                            }
                                            
                                            // Song info
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "$friendName is playing",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = textColor.copy(alpha = 0.7f)
                                                )
                                                
                                                Text(
                                                    text = if (activity.is_playing) "Now Playing" else "Paused",
                                                    style = MaterialTheme.typography.labelLarge.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = if (activity.is_playing) onlineColor else offlineColor
                                                )
                                                
                                                Text(
                                                    text = activity.title,
                                                    style = MaterialTheme.typography.titleLarge.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = textColor,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                
                                                Text(
                                                    text = activity.artist,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = textColor.copy(alpha = 0.7f),
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                
                                                activity.album?.let { album ->
                                                    Text(
                                                        text = album,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = textColor.copy(alpha = 0.5f),
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } ?: run {
                                    // No activity section
                                    val friendName = friend.profile.display_name ?: friend.profile.username
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(20.dp),
                                        color = backgroundColor,
                                        border = BorderStroke(1.dp, textColor.copy(alpha = 0.1f))
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .padding(40.dp)
                                                .fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.alert),
                                                contentDescription = "Not Listening",
                                                tint = textColor.copy(alpha = 0.3f),
                                                modifier = Modifier.size(64.dp)
                                            )
                                            Text(
                                                text = "Not Listening",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = textColor.copy(alpha = 0.5f)
                                            )
                                            Text(
                                                text = "$friendName is not currently listening to music",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = textColor.copy(alpha = 0.4f),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    if (friendsActivity.size > 1) {
                        item {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Friends (${friendsActivity.size})",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                LazyColumn(
                                    modifier = Modifier.height(120.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(friendsActivity) { friend ->
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val index = friendsActivity.indexOf(friend)
                                                    if (index != -1) {
                                                        selectedFriendIndex = index
                                                    }
                                                },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (friend == friendsActivity.getOrNull(selectedFriendIndex)) primaryColor.copy(alpha = 0.1f) else Color.Transparent,
                                            border = BorderStroke(
                                                width = if (friend == friendsActivity.getOrNull(selectedFriendIndex)) 2.dp else 1.dp,
                                                color = if (friend == friendsActivity.getOrNull(selectedFriendIndex)) primaryColor else secondaryColor.copy(alpha = 0.1f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                // Online indicator
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (friend.is_online) onlineColor else Color.Gray
                                                        )
                                                )
                                                
                                                // Profile initial
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(primaryColor),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = friend.profile.username.take(1).uppercase(),
                                                        style = MaterialTheme.typography.bodyLarge.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White
                                                        )
                                                    )
                                                }
                                                
                                                Column(
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = friend.profile.display_name ?: friend.profile.username,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = FontWeight.Medium
                                                        ),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    
                                                    friend.activity?.let { activity ->
                                                        Text(
                                                            text = "${activity.title} • ${activity.artist}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    } ?: run {
                                                        Text(
                                                            text = "Not listening",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Bottom buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Add friend button
            Button(
                onClick = { navController.navigate("cubicjam_add_friend") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.person),
                    contentDescription = "Add Friend",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Friend")
            }
            
            // Refresh button
            Button(
                onClick = {
                    scope.launch {
                        fetchFriends()
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = secondaryColor,
                    contentColor = Color.White
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.refresh),
                        contentDescription = "Refresh",
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh")
            }
        }
    }
}