package it.fast4x.rimusic.ui.screens.cubicjam

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import app.kreate.android.R
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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import it.fast4x.innertube.Innertube
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CubicJamScreen(
    navController: NavController,
    cubicJamManager: CubicJamManager? = null
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
    var isEnabled by remember { 
        mutableStateOf(preferences.getBoolean("is_enabled", false)) 
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
    
    // New state variables
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var friendCodeInput by remember { mutableStateOf("") }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var isLoadingFriends by remember { mutableStateOf(false) }
    var isLoadingProfile by remember { mutableStateOf(false) }
    var friendsActivity by remember { mutableStateOf<List<FriendActivity>>(emptyList()) }
    var selectedFriend by remember { mutableStateOf<UserProfileResponse?>(null) }
    var showFriendProfile by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Custom colors for theme
    val purple = Color(0xFF7C4DFF)
    val orange = Color(0xFFFF9800)
    val plantoneRed = Color(0xFFF44336)
    val onlineGreen = Color(0xFF4CAF50)
    
    // Fetch friends activity on screen focus
    LaunchedEffect(isLoggedIn, isEnabled) {
        if (isLoggedIn && isEnabled) {
            // Initial fetch
            refreshFriendsActivity(preferences, json)?.let {
                friendsActivity = it.friends
            }
            
            // Set up periodic refresh every 15 seconds
            while (true) {
                delay(15000)
                if (isLoggedIn && isEnabled) {
                    refreshFriendsActivity(preferences, json)?.let {
                        friendsActivity = it.friends
                    }
                }
            }
        }
    }
    
    // If not logged in, show auth screen
    if (!isLoggedIn) {
        CubicJamAuth(navController = navController)
        return
    }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Cubic Jam",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = purple
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            painter = painterResource(R.drawable.chevron_back),
                            contentDescription = "Back",
                            tint = purple
                        )
                    }
                },
                actions = {
                    // Open WebView button
                    IconButton(onClick = { 
                        navController.navigate("cubicjam_web") 
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.globe),
                            contentDescription = "Open Web",
                            tint = orange
                        )
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(
                            painter = painterResource(R.drawable.logout),
                            contentDescription = "Logout",
                            tint = plantoneRed
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddFriendDialog = true },
                containerColor = purple,
                contentColor = Color.White,
                modifier = Modifier.padding(bottom = 80.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.person),
                    contentDescription = "Add Friend"
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error message
            errorMessage?.let { message ->
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        color = plantoneRed.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, plantoneRed)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.alert_circle),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = plantoneRed
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                color = plantoneRed
                            )
                            IconButton(
                                onClick = { errorMessage = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = "Dismiss",
                                    tint = plantoneRed
                                )
                            }
                        }
                    }
                }
            }
            
            item {
                // User Profile Card with gradient
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = purple.copy(alpha = 0.1f),
                    border = BorderStroke(2.dp, purple.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Avatar with gradient
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                            colors = listOf(purple, orange)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = username?.take(1)?.uppercase() ?: "U",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White
                                )
                            }
                            
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                displayName?.let { name ->
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = purple
                                    )
                                }
                                
                                Text(
                                    text = "@${username ?: "user"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = purple.copy(alpha = 0.8f)
                                )
                                
                                friendCode?.let { code ->
                                    Text(
                                        text = "Friend Code: $code",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = orange
                                    )
                                }
                            }
                        }
                        
                        // Share Toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { enabled ->
                                    isEnabled = enabled
                                    preferences.edit()
                                        .putBoolean("is_enabled", enabled)
                                        .apply()
                                    if (!enabled) {
                                        // Clear friends activity when disabled
                                        friendsActivity = emptyList()
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = purple,
                                    checkedTrackColor = purple.copy(alpha = 0.5f),
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.Gray.copy(alpha = 0.5f)
                                )
                            )
                            
                            Column {
                                Text(
                                    text = "Share Activity",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = purple
                                )
                                Text(
                                    text = if (isEnabled) "Your friends can see what you're listening to" 
                                          else "You're not sharing your activity",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = purple.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
            
            // Friends Activity Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Friends Activity",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = orange
                        )
                    )
                    
                    // Refresh button
                    IconButton(
                        onClick = {
                            scope.launch {
                                isLoadingFriends = true
                                refreshFriendsActivity(preferences, json)?.let {
                                    friendsActivity = it.friends
                                }
                                isLoadingFriends = false
                            }
                        },
                        enabled = !isLoadingFriends && isEnabled
                    ) {
                        if (isLoadingFriends) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = orange
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.refresh),
                                contentDescription = "Refresh",
                                tint = orange
                            )
                        }
                    }
                }
            }
            
            if (!isEnabled) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Gray.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.eye_off),
                                contentDescription = "Sharing Disabled",
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Sharing Disabled",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color.Gray
                            )
                            Text(
                                text = "Enable sharing to see what your friends are listening to",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray.copy(alpha = 0.6f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    isEnabled = true
                                    preferences.edit()
                                        .putBoolean("is_enabled", true)
                                        .apply()
                                },
                                modifier = Modifier.padding(top = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = purple,
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Enable Sharing")
                            }
                        }
                    }
                }
            } else if (isLoadingFriends && friendsActivity.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = purple)
                    }
                }
            } else if (friendsActivity.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = orange.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, orange.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(32.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.people),
                                contentDescription = "No Friends",
                                tint = orange,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No Friends Yet",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = orange
                            )
                            Text(
                                text = "Add friends to see what they're listening to",
                                style = MaterialTheme.typography.bodySmall,
                                color = orange.copy(alpha = 0.6f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = { showAddFriendDialog = true },
                                modifier = Modifier.padding(top = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = orange,
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Add Friend")
                            }
                        }
                    }
                }
            } else {
                items(friendsActivity) { friend ->
                    FriendActivityCard(
                        friend = friend,
                        purple = purple,
                        orange = orange,
                        onlineGreen = onlineGreen,
                        onClick = {
                            scope.launch {
                                isLoadingProfile = true
                                val token = preferences.getString("bearer_token", null)
                                if (token != null) {
                                    try {
                                        selectedFriend = getUserProfile(token, friend.profile.username, json)
                                        showFriendProfile = true
                                    } catch (e: Exception) {
                                        Timber.e(e, "Failed to load friend profile")
                                        errorMessage = "Failed to load profile: ${e.message}"
                                    }
                                }
                                isLoadingProfile = false
                            }
                        }
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
    
    // Add Friend Dialog
    if (showAddFriendDialog) {
        AlertDialog(
            onDismissRequest = { showAddFriendDialog = false },
            title = { 
                Text("Add Friend", color = purple)
            },
            text = {
                Column {
                    Text(
                        text = "Enter your friend's 8-character code:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                        color = purple.copy(alpha = 0.8f)
                    )
                    OutlinedTextField(
                        value = friendCodeInput,
                        onValueChange = { 
                            friendCodeInput = it.uppercase().take(8)
                        },
                        placeholder = { Text("e.g., ABC12345") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = purple,
                            unfocusedBorderColor = purple.copy(alpha = 0.5f)
                        ),
                        supportingText = {
                            if (friendCodeInput.length == 8) {
                                Text("Valid friend code format", color = onlineGreen)
                            }
                        },
                        isError = friendCodeInput.isNotEmpty() && friendCodeInput.length != 8
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val token = preferences.getString("bearer_token", null)
                            if (token != null && friendCodeInput.length == 8) {
                                try {
                                    addFriend(token, friendCodeInput, json)
                                    showAddFriendDialog = false
                                    friendCodeInput = ""
                                    errorMessage = "Friend request sent successfully!"
                                    
                                    // Refresh friends list
                                    refreshFriendsActivity(preferences, json)?.let {
                                        friendsActivity = it.friends
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to add friend")
                                    errorMessage = "Failed to add friend: ${e.message}"
                                }
                            } else if (friendCodeInput.length != 8) {
                                errorMessage = "Friend code must be 8 characters"
                            }
                        }
                    },
                    enabled = friendCodeInput.length == 8
                ) {
                    Text("Send Request", color = purple)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddFriendDialog = false }
                ) {
                    Text("Cancel", color = plantoneRed)
                }
            }
        )
    }
    
    // Friend Profile Dialog
    if (showFriendProfile) {
        selectedFriend?.let { profile ->
            FriendProfileDialog(
                profile = profile,
                purple = purple,
                orange = orange,
                plantoneRed = plantoneRed,
                onDismiss = { showFriendProfile = false },
                isLoading = isLoadingProfile
            )
        }
    }
    
    // Logout Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { 
                Text("Disconnect from Cubic Jam?", color = plantoneRed)
            },
            text = { 
                Text("Your listening activity will no longer be shared with friends.", color = Color.Gray) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        preferences.edit().clear().apply()
                        cubicJamManager?.onStop()
                        navController.navigateUp()
                    }
                ) {
                    Text("Disconnect", color = plantoneRed)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false }
                ) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun FriendActivityCard(
    friend: FriendActivity,
    purple: Color,
    orange: Color,
    onlineGreen: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = purple.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, purple.copy(alpha = 0.2f)),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Online indicator
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (friend.is_online) onlineGreen 
                        else Color.Gray
                    )
            )
            
            // Profile image or placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(purple, orange)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = friend.profile.username.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = friend.profile.display_name ?: friend.profile.username,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = purple
                    )
                    
                    if (!friend.is_online) {
                        Text(
                            text = "• Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
                
                friend.activity?.let { activity ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                if (activity.is_playing) R.drawable.play 
                                else R.drawable.pause
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = orange
                        )
                        
                        Text(
                            text = "${activity.title} • ${activity.artist}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } ?: run {
                    Text(
                        text = "Not listening",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            
            Icon(
                painter = painterResource(R.drawable.chevron_forward),
                contentDescription = "View Profile",
                tint = purple.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun FriendProfileDialog(
    profile: UserProfileResponse,
    purple: Color,
    orange: Color,
    plantoneRed: Color,
    onDismiss: () -> Unit,
    isLoading: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(purple, orange)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = profile.profile.username.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
                Column {
                    Text(
                        text = profile.profile.display_name ?: profile.profile.username,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = purple
                    )
                    Text(
                        text = "@${profile.profile.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        },
        text = {
            Column {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = purple)
                    }
                } else {
                    // Current Activity
                    profile.current_activity?.let { activity ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = purple.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, purple.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Artwork
                                activity.artwork_url?.let { url ->
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } ?: run {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(purple),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.music),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = activity.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = purple
                                    )
                                    Text(
                                        text = activity.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (activity.is_playing) "Now Playing" 
                                              else "Paused",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = orange
                                    )
                                }
                            }
                        }
                    }
                    
                    // Last 4 Songs
                    if (profile.recent_tracks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Recent Tracks",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(bottom = 8.dp),
                            color = orange
                        )
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            profile.recent_tracks.take(4).forEach { track ->
                                RecentTrackItem(track = track, purple = purple)
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No recent tracks",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = purple)
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
fun RecentTrackItem(track: RecentTrack, purple: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Track artwork
        track.artwork_url?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        } ?: run {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(purple.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.music),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = purple
                )
            }
        }
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = purple
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Icon(
            painter = painterResource(R.drawable.history),
            contentDescription = "Played",
            modifier = Modifier.size(16.dp),
            tint = Color.Gray
        )
    }
}

// Helper functions for API calls
private suspend fun refreshFriendsActivity(
    preferences: android.content.SharedPreferences,
    json: Json
): FriendsActivityResponse? {
    val token = preferences.getString("bearer_token", null)
    if (token == null) return null
    
    return try {
        val response = Innertube.client.get("https://dkbvirgavjojuyaazzun.supabase.co/functions/v1/friends-activity") {
            header("Authorization", "Bearer $token")
        }
        json.decodeFromString<FriendsActivityResponse>(response.body())
    } catch (e: Exception) {
        Timber.e(e, "Failed to fetch friends activity")
        null
    }
}

private suspend fun getUserProfile(
    token: String,
    username: String,
    json: Json
): UserProfileResponse {
    val response = Innertube.client.get("https://dkbvirgavjojuyaazzun.supabase.co/functions/v1/user-profile") {
        header("Authorization", "Bearer $token")
        parameter("username", username)
    }
    return json.decodeFromString<UserProfileResponse>(response.body())
}

private suspend fun addFriend(
    token: String,
    friendCode: String,
    json: Json
) {
    val response = Innertube.client.post("https://dkbvirgavjojuyaazzun.supabase.co/functions/v1/friends") {
        header("Authorization", "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(FriendRequest(action = "add", friend_code = friendCode))
    }
    val result = json.decodeFromString<FriendResponse>(response.body())
    if (!result.success) {
        throw Exception(result.message ?: "Failed to add friend")
    }
}

// API Data Classes
@Serializable
data class FriendActivity(
    val profile: FriendProfile,
    val activity: Activity?,
    val is_online: Boolean
)

@Serializable
data class FriendProfile(
    val id: String,
    val username: String,
    val display_name: String? = null,
    val avatar_url: String? = null
)

@Serializable
data class Activity(
    val track_id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artwork_url: String? = null,
    val is_playing: Boolean,
    val position_ms: Long? = null,
    val duration_ms: Long? = null,
    val updated_at: String
)

@Serializable
data class UserProfileResponse(
    val profile: UserProfile,
    val recent_tracks: List<RecentTrack>,
    val current_activity: CurrentActivity? = null,
    val is_friend: Boolean
)

@Serializable
data class UserProfile(
    val id: String,
    val username: String,
    val display_name: String? = null,
    val avatar_url: String? = null,
    val bio: String? = null,
    val friend_code: String,
    val is_activity_public: Boolean
)

@Serializable
data class RecentTrack(
    val id: String,
    val track_id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artwork_url: String? = null,
    val listened_at: String
)

@Serializable
data class CurrentActivity(
    val track_id: String,
    val title: String,
    val artist: String,
    val is_playing: Boolean,
    val artwork_url: String? = null
)

@Serializable
data class FriendsActivityResponse(
    val friends: List<FriendActivity>
)

@Serializable
data class FriendRequest(
    val action: String,
    val friend_code: String? = null,
    val friendship_id: String? = null
)

@Serializable
data class FriendResponse(
    val success: Boolean,
    val message: String? = null,
    val friendship: Map<String, String>? = null
)