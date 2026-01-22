package it.fast4x.rimusic.ui.screens.cubicjam

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import app.kreate.android.R
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CubicJamScreen(
    navController: NavController,
    cubicJamManager: CubicJamManager? = null
) {
    val context = LocalContext.current
    
    // Load from SharedPreferences
    val preferences = remember { context.getSharedPreferences("cubic_jam_prefs", Context.MODE_PRIVATE) }
    
    // State for UI updates
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
    var showLogoutDialog by remember { mutableStateOf(false) }
    
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
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            painter = painterResource(app.kreate.android.R.drawable.chevron_back),
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(
                            painter = painterResource(app.kreate.android.R.drawable.logout),
                            contentDescription = "Logout"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // User info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = username?.take(1)?.uppercase() ?: "U",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        
                        Column {
                            Text(
                                text = "@${username ?: "user"}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            displayName?.let { name ->
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            friendCode?.let { code ->
                                Text(
                                    text = "Friend code: $code",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    
                    // Status
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isEnabled) 
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (isEnabled) app.kreate.android.R.drawable.checkmark 
                                    else app.kreate.android.R.drawable.music
                                ),
                                contentDescription = null,
                                tint = if (isEnabled) 
                                    MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isEnabled) 
                                    "Sharing your listening activity"
                                    else "Listening activity sharing is off",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isEnabled)
                                    MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // Enable/disable toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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
                            }
                        )
                        
                        Column {
                            Text(
                                text = "Share Listening Activity",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Automatically update your Cubic Jam profile when you listen to music",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    if (isEnabled) {
                        Text(
                            text = "Your friends can see what you're listening to in real-time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            // Information card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "About Cubic Jam",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = "• Share what you're listening to with friends\n" +
                              "• See what your friends are playing\n" +
                              "• Updates automatically every 15 seconds",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Logout button
            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    painter = painterResource(app.kreate.android.R.drawable.logout),
                    contentDescription = "Logout"
                )
                Spacer(Modifier.width(8.dp))
                Text("Disconnect from Cubic Jam")
            }
        }
    }
    
    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Disconnect from Cubic Jam?") },
            text = { 
                Text("Your listening activity will no longer be shared with friends.") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        preferences.edit().clear().apply()
                        cubicJamManager?.onStop()
                        navController.navigateUp()
                    }
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}