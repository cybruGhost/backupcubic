// ui/screens/welcome/WelcomeScreen.kt - FIXED VERSION
package it.fast4x.rimusic.ui.screens.welcome

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.kreate.android.R
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.utils.DataStoreUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.sin

// Key constants matching your WelcomeMessage.kt
private const val KEY_USERNAME = "username"
private const val KEY_CITY = "weather_city"
private const val KEY_HAS_SEEN_WELCOME = "has_seen_welcome"

// Copy the getLocationFromIP function here since it's private in utils
private suspend fun getLocationFromIP(): String? = withContext(Dispatchers.IO) {
    return@withContext try {
        val url = URL("https://ipapi.co/json/")
        val connection = withContext(Dispatchers.IO) { url.openConnection() as HttpURLConnection }
        connection.requestMethod = "GET"
        connection.connectTimeout = 3000 // Reduced timeout
        connection.readTimeout = 3000

        val responseCode = connection.responseCode
        if (responseCode == 200) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.use { it.readText() }
            reader.close()
            connection.disconnect()
            val json = JSONObject(response)
            json.optString("city", "Nairobi")
        } else "Nairobi"
    } catch (e: Exception) {
        e.printStackTrace()
        "Nairobi"
    }
}

@Composable
fun WelcomeScreen(navController: NavController) {
    val context = LocalContext.current
    var showWelcome by remember { mutableStateOf(true) }
    var userHasSeenWelcome by remember { 
        mutableStateOf(false)
    }
    
    // Load initial state asynchronously
    LaunchedEffect(Unit) {
        val hasSeen = DataStoreUtils.getStringBlocking(context, KEY_HAS_SEEN_WELCOME, "")
        userHasSeenWelcome = hasSeen == "true"
        
        // If city is not set, try to get location from IP (non-blocking)
        if (!userHasSeenWelcome) {
            withContext(Dispatchers.IO) {
                val currentCity = DataStoreUtils.getStringBlocking(context, KEY_CITY, "")
                if (currentCity.isBlank()) {
                    try {
                        val detectedCity = getLocationFromIP() ?: "Nairobi"
                        DataStoreUtils.saveStringBlocking(context, KEY_CITY, detectedCity)
                    } catch (e: Exception) {
                        // Silently fail, default city will be used
                    }
                }
            }
        }
    }
    
    // Optimized canvas background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        // Performant animated liquid effect
        LiquidBackground(modifier = Modifier.fillMaxSize())
        
        // Subtle noise overlay for texture
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        // Subtle noise for texture
                        for (i in 0..100) {
                            val x = (Math.random() * size.width).toFloat()
                            val y = (Math.random() * size.height).toFloat()
                            val radius = (Math.random() * 1.5f).toFloat()
                            drawCircle(
                                color = Color(0x0AFFFFFF),
                                radius = radius,
                                center = Offset(x, y)
                            )
                        }
                    }
                }
        )
        
        if (userHasSeenWelcome && showWelcome) {
            // User has already completed welcome, go to home
            LaunchedEffect(Unit) {
                delay(300) // Reduced delay
                navController.navigate(NavRoutes.home.name) {
                    popUpTo(NavRoutes.welcome.name) { inclusive = true }
                }
            }
            
            // Loading screen
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFFFF6BCB),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Welcome back!",
                    color = Color.White,
                    fontSize = 16.sp, // Slightly smaller
                    fontWeight = FontWeight.Medium
                )
            }
        } else if (showWelcome) {
            // Show welcome screen - CANNOT BE EXITED
            WelcomeContent(
                onComplete = { name, city ->
                    // Save data to DataStore asynchronously
                    DataStoreUtils.saveStringBlocking(context, KEY_HAS_SEEN_WELCOME, "true")
                    DataStoreUtils.saveStringBlocking(context, KEY_USERNAME, name)
                    if (city.isNotBlank()) {
                        DataStoreUtils.saveStringBlocking(context, KEY_CITY, city)
                    }
                    showWelcome = false
                }
            )
        } else {
            // Transition to home
            LaunchedEffect(Unit) {
                delay(300) // Reduced delay
                navController.navigate(NavRoutes.home.name) {
                    popUpTo(NavRoutes.welcome.name) { inclusive = true }
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFFFF6BCB),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Getting everything ready...",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeContent(onComplete: (String, String) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var animated by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (animated) 1f else 0f,
        animationSpec = tween(durationMillis = 600), // Faster animation
        label = "welcomeAnimation"
    )
    val cardElevation by animateDpAsState(
        targetValue = if (animated) 8.dp else 0.dp,
        animationSpec = tween(durationMillis = 800)
    )
    
    LaunchedEffect(Unit) {
        animated = true
        // Load detected city from IP
        city = DataStoreUtils.getStringBlocking(context, KEY_CITY, "")
    }
    
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp), // Reduced padding
            contentAlignment = Alignment.Center
        ) {
            // Decorative floating elements
            AnimatedFloatingElements()
            
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.height(40.dp))
                
                // Logo/Icon with optimized animation
                Box(
                    modifier = Modifier
                        .size(100.dp) // Smaller for better performance
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFF6BCB),
                                    Color(0xFF9C27B0),
                                    Color(0xFF1A0036)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFF6BCB),
                                    Color(0xFF9C27B0)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.cubic),
                        contentDescription = "Cubic Logo",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
                
                // Title section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .alpha(if (animated) 1f else 0f)
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = "🎵",
                        color = Color.White,
                        fontSize = 32.sp, // Smaller
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Let's get started",
                        color = Color.White,
                        fontSize = 22.sp, // Smaller
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    
                    Text(
                        text = "Personalize your music journey",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                // Input Card
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (animated) 1f else 0f)
                        .graphicsLayer {
                            translationY = cardElevation.value * -0.5f
                        },
                    shape = RoundedCornerShape(24.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        contentColor = Color.White
                    ),
                    elevation = androidx.compose.material3.CardDefaults.cardElevation(
                        defaultElevation = cardElevation
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        // Name Input
                        Text(
                            text = "Your name",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        OutlinedTextField(
                            value = name,
                            onValueChange = { 
                                if (it.length <= 14) {
                                    name = it
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { 
                                Text(
                                    "Enter your name",
                                    color = Color.White.copy(alpha = 0.5f)
                                ) 
                            },
                            singleLine = true,
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color.White,
                                focusedBorderColor = Color(0xFFFF6BCB),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                errorBorderColor = Color(0xFFFF5252)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            supportingText = {
                                Text(
                                    text = "${name.length}/14",
                                    color = if (name.length > 14) Color(0xFFFF5252) else Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            },
                            isError = name.length > 14
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // City Input
                        Text(
                            text = "Your city (optional)",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        OutlinedTextField(
                            value = city,
                            onValueChange = { 
                                if (it.length <= 14) {
                                    city = it
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { 
                                Text(
                                    "e.g., Tokyo, London",
                                    color = Color.White.copy(alpha = 0.5f)
                                ) 
                            },
                            singleLine = true,
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color.White,
                                focusedBorderColor = Color(0xFF9C27B0),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            supportingText = {
                                Text(
                                    text = "${city.length}/14",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Helper text
                        Text(
                            text = "📍 We'll use this to personalize your experience",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    }
                }
                
                // Continue Button
                Button(
                    onClick = {
                        if (name.isNotBlank() && name.length <= 14) {
                            onComplete(name.trim(), city.trim())
                        }
                    },
                    enabled = name.isNotBlank() && name.length <= 14,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .alpha(if (animated) 1f else 0f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF6BCB),
                        contentColor = Color.White,
                        disabledContainerColor = Color.Gray.copy(alpha = 0.2f),
                        disabledContentColor = Color.White.copy(alpha = 0.3f)
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 2.dp,
                        disabledElevation = 0.dp
                    )
                ) {
                    Text(
                        "Get Started",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 0.5.sp
                    )
                }
                
                // Terms and Privacy
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .alpha(if (animated) 1f else 0f)
                        .padding(bottom = 24.dp)
                ) {
                    Text(
                        text = "By continuing, you agree to our",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Box(
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { /* Handle terms click */ }
                        )
                    ) {
                        Text(
                            text = "Terms of Service • Privacy Policy",
                            color = Color(0xFFFF6BCB),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Mandatory notice
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Color(0xFF9C27B0).copy(alpha = 0.15f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "✨ Complete this once to begin your journey",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimatedFloatingElements() {
    // Simple floating circles with minimal animation
    val density = LocalDensity.current
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .blur(8.dp)
    ) {
        // Minimal decorative elements
        repeat(6) { index ->
            val size = (20 + index * 8).dp
            val offsetX = (index * 30).dp
            val offsetY = (index * 40).dp
            
            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        alpha = 0.1f
                        translationX = offsetX.value * density.density
                        translationY = offsetY.value * density.density
                    }
                    .background(
                        color = Color(0xFFFF6BCB).copy(alpha = 0.1f),
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun LiquidBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .drawWithCache {
                val width = size.width
                val height = size.height
                
                val liquidBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1A0036),
                        Color(0xFF2D0049),
                        Color(0xFF4A1B6D),
                        Color(0xFF1A0036)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(width, height)
                )
                
                onDrawWithContent {
                    // Draw base gradient
                    drawRect(brush = liquidBrush)
                    
                    // Simple liquid effect with minimal calculations
                    val time = System.currentTimeMillis() / 1000f
                    val waveHeight = 30f
                    val waveSpeed = 0.5f
                    
                    // Draw waves with clipping for performance
                    clipRect {
                        for (i in 0..2) {
                            val offset = i * 100f
                            val path = Path().apply {
                                moveTo(0f, height)
                                for (x in 0..width.toInt() step 10) {
                                    val y = height * 0.7f + 
                                           sin((x + offset + time * waveSpeed * 100) * 0.01f) * waveHeight
                                    lineTo(x.toFloat(), y)
                                }
                                lineTo(width, height)
                                close()
                            }
                            
                            drawPath(
                                path = path,
                                color = Color(0xFF9C27B0).copy(alpha = 0.1f + i * 0.05f),
                                blendMode = BlendMode.Screen
                            )
                        }
                    }
                    
                    // Draw subtle particles
                    for (i in 0..20) {
                        val x = (Math.random() * width).toFloat()
                        val y = (Math.random() * height).toFloat()
                        val radius = (Math.random() * 3f).toFloat()
                        drawCircle(
                            color = Color(0xFFFF6BCB).copy(alpha = 0.05f),
                            radius = radius,
                            center = Offset(x, y)
                        )
                    }
                }
            }
    )
}