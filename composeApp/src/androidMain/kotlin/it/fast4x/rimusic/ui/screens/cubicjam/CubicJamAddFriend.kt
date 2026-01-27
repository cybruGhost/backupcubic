package it.fast4x.rimusic.ui.screens.cubicjam

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.kreate.android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber

@Composable
fun CubicJamAddFriend(
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val json = Json { ignoreUnknownKeys = true }
    
    val preferences = remember {
        context.getSharedPreferences("cubic_jam_prefs", Context.MODE_PRIVATE)
    }
    
    var friendCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    
    val primaryColor = Color(0xFF7C4DFF)
    val backgroundColor = MaterialTheme.colorScheme.surface
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(
                onClick = { navController.navigateUp() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.chevron_back),
                    contentDescription = "Back",
                    tint = primaryColor
                )
            }
            
            Text(
                text = "Add Friend",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
            )
        }
        
        // Instructions
        Text(
            text = "Enter your friend's 8-character Cubic Jam code to send them a friend request.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        
        // Friend code input
        OutlinedTextField(
            value = friendCode,
            onValueChange = {
                friendCode = it.uppercase().take(8)
                errorMessage = null
                successMessage = null
            },
            label = { Text("Friend Code (8 characters)") },
            placeholder = { Text("e.g., ABC12345") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                unfocusedBorderColor = primaryColor.copy(alpha = 0.5f),
                focusedLabelColor = primaryColor,
                focusedTextColor = primaryColor
            ),
            isError = friendCode.isNotEmpty() && friendCode.length != 8,
            supportingText = {
                if (friendCode.length == 8) {
                    Text("✓ Valid code format", color = Color.Green)
                } else if (friendCode.isNotEmpty()) {
                    Text("Code must be 8 characters", color = Color.Red)
                }
            }
        )
        
        // Error/Success messages
        errorMessage?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
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
                }
            }
        }
        
        successMessage?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color.Green.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, Color.Green.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.checkmark),
                        contentDescription = "Success",
                        tint = Color.Green,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Green,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { navController.navigateUp() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("Cancel")
            }
            
            Button(
                onClick = {
                    if (friendCode.length == 8) {
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            successMessage = null
                            
                            try {
                                addFriend(preferences, friendCode, json)
                                successMessage = "Friend request sent successfully!"
                                friendCode = ""
                                
                                // Navigate back after 2 seconds
                                scope.launch {
                                    kotlinx.coroutines.delay(2000)
                                    navController.navigateUp()
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to add friend")
                                errorMessage = when {
                                    e.message?.contains("already", ignoreCase = true) == true -> 
                                        "You're already friends with this user"
                                    e.message?.contains("not found", ignoreCase = true) == true -> 
                                        "Friend code not found"
                                    e.message?.contains("yourself", ignoreCase = true) == true -> 
                                        "You can't add yourself"
                                    else -> "Failed to add friend: ${e.message}"
                                }
                            } finally {
                                isLoading = false
                            }
                        }
                    } else {
                        errorMessage = "Please enter a valid 8-character friend code"
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = friendCode.length == 8 && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
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
                    Text("Send Request")
                }
            }
        }
    }
}