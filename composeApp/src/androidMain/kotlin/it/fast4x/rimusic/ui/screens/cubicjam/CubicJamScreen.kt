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
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlinx.serialization.json.Json

@Composable
fun CubicJamScreen(
    navController: NavController,
    cubicJamManager: CubicJamManager? = null  // ADD THIS PARAMETER
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val json = Json { ignoreUnknownKeys = true }
    
    // Load from SharedPreferences
    val preferences = remember { 
        context.getSharedPreferences("cubic_jam_prefs", Context.MODE_PRIVATE) 
    }
    
    // State variables
    val isLoggedIn by remember(preferences) { 
        mutableStateOf(preferences.getString("bearer_token", null) != null) 
    }
    val username by remember(preferences) { 
        mutableStateOf(preferences.getString("username", null)) 
    }
    val displayName by remember(preferences) { 
        mutableStateOf(preferences.getString("display_name", null)) 
    }
    val friendCode by remember(preferences) { 
        mutableStateOf(preferences.getString("friend_code", null)) 
    }
    
    // Friends state
    var friendsActivity by remember { mutableStateOf<List<FriendActivity>>(emptyList()) }
    var selectedFriendIndex by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Colors - dynamic based on album art
    val primaryColor = remember { Color(0xFF7C4DFF) }
    val secondaryColor = remember { Color(0xFFFF9800) }
    val backgroundColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val onlineColor = Color(0xFF4CAF50)
    val offlineColor = Color(0xFF757575)
    
    // Function to fetch friends
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
                val filteredFriends = result.friends.filter { friend ->
                    friend.profile.username != username // Don't show ourselves
                }
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
    
    // Function to test API
    suspend fun testApi() {
        try {
            val result = testFriendsActivityAPI(preferences)
            Timber.tag("CubicJam").d("API test result: $result")
        } catch (e: Exception) {
            Timber.tag("CubicJam").e(e, "API test failed")
        }
    }
    
    // Initial fetch and periodic refresh
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            Timber.tag("CubicJam").d("User is logged in, starting fetch...")
            
            // First test the API
            scope.launch {
                testApi()
            }
            
            // Then fetch friends
            fetchFriends()
            
            // Refresh every 10 seconds
            while (true) {
                delay(10000)
                if (isLoggedIn) {
                    fetchFriends()
                }
            }
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
    
    // Dynamic background based on album art
    val surfaceColor = primaryColor.copy(alpha = 0.1f)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        // Top user info section
        UserHeaderSection(
            username = username,
            displayName = displayName,
            friendCode = friendCode,
            primaryColor = primaryColor,
            onProfileClick = {
                username?.let { name ->
                    navController.navigate("cubicjam_web?url=https://jam-wave-connect.lovable.app/profile/$name")
                }
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Error message
        ErrorMessageSection(
            errorMessage = errorMessage,
            onDismiss = { errorMessage = null }
        )
        
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
                            SelectedFriendSection(
                                friend = friend,
                                primaryColor = primaryColor,
                                secondaryColor = secondaryColor,
                                onlineColor = onlineColor,
                                offlineColor = offlineColor,
                                textColor = textColor,
                                surfaceColor = surfaceColor,
                                onProfileClick = { username ->
                                    navController.navigate("cubicjam_web?url=https://jam-wave-connect.lovable.app/profile/$username")
                                },
                                backgroundColor = backgroundColor
                            )
                        }
                    }
                    
                    if (friendsActivity.size > 1) {
                        item {
                            FriendSelectorSection(
                                friends = friendsActivity,
                                selectedIndex = selectedFriendIndex,
                                primaryColor = primaryColor,
                                secondaryColor = secondaryColor,
                                onlineColor = onlineColor,
                                onFriendSelected = { index -> selectedFriendIndex = index }
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Bottom buttons
        BottomButtonsSection(
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            isLoading = isLoading,
            onAddFriend = { navController.navigate("cubicjam_add_friend") },
            onRefresh = {
                scope.launch {
                    fetchFriends()
                }
            }
        )
    }
}
@Composable
private fun UserHeaderSection(
    username: String?,
    displayName: String?,
    friendCode: String?,
    primaryColor: Color,
    onProfileClick: () -> Unit
) {
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
            onClick = onProfileClick,
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
}

@Composable
private fun ErrorMessageSection(
    errorMessage: String?,
    onDismiss: () -> Unit
) {
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
                    onClick = onDismiss,
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
}

@Composable
private fun SelectedFriendSection(
    friend: FriendActivity,
    primaryColor: Color,
    secondaryColor: Color,
    onlineColor: Color,
    offlineColor: Color,
    textColor: Color,
    surfaceColor: Color,
    backgroundColor: Color,
    onProfileClick: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Friend info header
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
                onClick = { onProfileClick(friend.profile.username) },
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
            NowPlayingSection(
                activity = activity,
                friendName = friend.profile.display_name ?: friend.profile.username,
                primaryColor = primaryColor,
                surfaceColor = surfaceColor,
                onlineColor = onlineColor,
                offlineColor = offlineColor,
                textColor = textColor
            )
        } ?: run {
            NoActivitySection(
                friendName = friend.profile.display_name ?: friend.profile.username,
                backgroundColor = backgroundColor,
                textColor = textColor
            )
        }
    }
}

@Composable
private fun NowPlayingSection(
    activity: Activity,
    friendName: String,
    primaryColor: Color,
    surfaceColor: Color,
    onlineColor: Color,
    offlineColor: Color,
    textColor: Color
) {
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
                AsyncImage(
                    model = url,
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
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
}

@Composable
private fun NoActivitySection(
    friendName: String,
    backgroundColor: Color,
    textColor: Color
) {
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

@Composable
private fun FriendSelectorSection(
    friends: List<FriendActivity>,
    selectedIndex: Int,
    primaryColor: Color,
    secondaryColor: Color,
    onlineColor: Color,
    onFriendSelected: (Int) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Friends",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        
        LazyColumn(
            modifier = Modifier.height(120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(friends) { friend ->
                FriendSelectorItem(
                    friend = friend,
                    isSelected = friend == friends.getOrNull(selectedIndex),
                    onClick = {
                        val index = friends.indexOf(friend)
                        if (index != -1) {
                            onFriendSelected(index)
                        }
                    },
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                    onlineColor = onlineColor
                )
            }
        }
    }
}

@Composable
private fun FriendSelectorItem(
    friend: FriendActivity,
    isSelected: Boolean,
    onClick: () -> Unit,
    primaryColor: Color,
    secondaryColor: Color,
    onlineColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) primaryColor.copy(alpha = 0.1f) else Color.Transparent,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) primaryColor else secondaryColor.copy(alpha = 0.1f)
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

@Composable
private fun BottomButtonsSection(
    primaryColor: Color,
    secondaryColor: Color,
    isLoading: Boolean,
    onAddFriend: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Add friend button
        Button(
            onClick = onAddFriend,
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
            onClick = onRefresh,
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