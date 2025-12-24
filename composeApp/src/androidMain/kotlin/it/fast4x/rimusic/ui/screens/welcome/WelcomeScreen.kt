package it.fast4x.rimusic.ui.screens.welcome

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.fast4x.rimusic.utils.DataStoreUtils

@Composable
fun WelcomeScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var showWelcome by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        val hasSeenWelcome = DataStoreUtils.getBooleanBlocking(
            context, 
            DataStoreUtils.KEY_HAS_SEEN_WELCOME, 
            false
        )
        showWelcome = !hasSeenWelcome
    }
    
    if (showWelcome) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎵",
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Text(
                    text = "Welcome to Cubic Music",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = "Your personal music streaming experience",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
                
                // You can add more content here like features, onboarding steps, etc.
                
                Button(
                    onClick = {
                        DataStoreUtils.saveBooleanBlocking(
                            context,
                            DataStoreUtils.KEY_HAS_SEEN_WELCOME,
                            true
                        )
                        onComplete()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        "Get Started",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    } else {
        onComplete()
    }
}