// ui/screens/welcome/WelcomeScreen.kt
package it.fast4x.rimusic.ui.screens.welcome

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.kreate.android.R
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.DataStoreUtils
import kotlinx.coroutines.delay

@Composable
fun WelcomeScreen(navController: NavController) {
    val context = LocalContext.current
    var userHasSeenWelcome by remember { 
        mutableStateOf(DataStoreUtils.getStringBlocking(context, "has_seen_welcome", "").isNotEmpty())
    }
    
    // Check if user has seen welcome before
    LaunchedEffect(Unit) {
        val hasSeen = DataStoreUtils.getStringBlocking(context, "has_seen_welcome", "")
        userHasSeenWelcome = hasSeen == "true"
        if (userHasSeenWelcome) {
            delay(500)
            navController.navigate(NavRoutes.home.name) {
                popUpTo(NavRoutes.welcome.name) { inclusive = true }
            }
        }
    }
    
    if (!userHasSeenWelcome) {
        WelcomePager(
            onComplete = { name, country ->
                DataStoreUtils.saveStringBlocking(context, "has_seen_welcome", "true")
                DataStoreUtils.saveStringBlocking(context, "user_name", name)
                DataStoreUtils.saveStringBlocking(context, "user_country", country)
                navController.navigate(NavRoutes.home.name) {
                    popUpTo(NavRoutes.welcome.name) { inclusive = true }
                }
            }
        )
    } else {
        // Loading screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A237E),
                            Color(0xFF0D47A1),
                            Color(0xFF1565C0)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Welcome back!",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}