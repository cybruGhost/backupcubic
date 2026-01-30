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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.kreate.android.R
import me.knighthat.utils.isNetworkAvailable

@Composable
fun CubicJamAddFriend(
    navController: NavController
) {
    val context = LocalContext.current
    
    val preferences = remember {
        context.getSharedPreferences("cubic_jam_prefs", Context.MODE_PRIVATE)
    }
    
    val bearerToken = preferences.getString("bearer_token", null)
    val isLoggedIn = bearerToken != null
    
    var showNoInternet by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    
    val primaryColor = Color(0xFF7C4DFF)
    
    LaunchedEffect(Unit) {
        // Check internet connection when screen loads
        val hasInternet = isNetworkAvailable(context)
        if (!hasInternet) {
            showNoInternet = true
        } else if (isLoggedIn) {
            // Navigate to WebView with the feed URL
            navController.navigate("cubicjam_web?url=https://jam-wave-connect.lovable.app/feed")
        } else {
            // If not logged in, navigate to auth screen
            navController.navigate("cubicjam_auth")
        }
    }
    
    if (showNoInternet) {
        NoInternetScreen(
            onRetry = {
                isLoading = true
                val hasInternet = isNetworkAvailable(context)
                showNoInternet = !hasInternet
                if (hasInternet && isLoggedIn) {
                    navController.navigate("cubicjam_web?url=https://jam-wave-connect.lovable.app/feed")
                }
                isLoading = false
            },
            onBack = { navController.navigateUp() }
        )
        return
    }
    
    // Show loading screen while redirecting
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = primaryColor)
            Text(
                text = "Redirecting to Cubic Jam...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun NoInternetScreen(
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    val primaryColor = Color(0xFF7C4DFF)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.chevron_back),
                    contentDescription = "Back",
                    tint = primaryColor
                )
            }
            
            Text(
                text = "Cubic Jam",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
            )
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // No internet icon
        Icon(
            painter = painterResource(R.drawable.alert_circle),
            contentDescription = "No Internet",
            tint = primaryColor.copy(alpha = 0.5f),
            modifier = Modifier.size(100.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Message
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "No Internet Connection",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "You need an internet connection to access Cubic Jam.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Action buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.refresh),
                    contentDescription = "Retry",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry Connection")
            }
            
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Go Back")
            }
        }
    }
}