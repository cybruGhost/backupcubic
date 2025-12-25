package it.fast4x.rimusic.ui.screens.rewind.slides

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius


@Composable
fun WelcomeSlide(username: String, year: Int, onNext: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0A0A0A),
                        Color(0xFF000000)
                )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo with padding and background to hide real borders
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF444444),
                                Color(0xFF222222),
                                Color(0xFF000000)
                            )
                        )
                    )
                    .drawWithContent {
                        drawContent()
                        // Draw border manually
                        drawCircle(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFFD700),
                                    Color(0xFFFFFF88),
                                    Color(0xFFFFD700)
                                )
                            ),
                            center = center,
                            radius = size.minDimension / 2 - 1.dp.toPx(),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                val context = LocalContext.current
                val resourceId = context.resources.getIdentifier(
                    "rewindlogo",
                    "drawable",
                    context.packageName
                )
                
                if (resourceId != 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF333333),
                                        Color(0xFF000000)
                                    )
                                )
                            )
                            .padding(8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = resourceId),
                            contentDescription = "Rewind Logo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            alpha = 0.9f
                        )
                    }
                } else {
                    // Fallback if logo not found
                    Text(
                        text = "REWIND",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 2.sp
                    )
                }
                
                // Glow effect
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0x00FFFFFF),
                                    Color(0x10FFD700),
                                    Color(0x00FFFFFF)
                                )
                            ),
                            shape = CircleShape
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Year with metallic effect
            Box(
                modifier = Modifier
                    .height(70.dp)
                    .fillMaxWidth(0.8f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF2A2A2A),
                                Color(0xFF1A1A1A),
                                Color(0xFF0A0A0A)
                            )
                        )
                    )
                    .drawWithContent {
                        drawContent()
                        // Draw border manually
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0x40FFD700),
                                    Color(0x80FFEC8B),
                                    Color(0x40FFD700)
                                )
                            ),
                            topLeft = androidx.compose.ui.geometry.Offset.Zero,
                            size = size,
                            cornerRadius = CornerRadius(20.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                    .graphicsLayer {
                        shadowElevation = 20.dp.toPx()
                        shape = RoundedCornerShape(20.dp)
                        clip = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "YOUR $year",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFE6E8FA),
                    letterSpacing = 4.sp,
                    modifier = Modifier
                        .drawWithContent {
                            drawContent()
                            // Add metallic shine
                            drawRect(
                                brush = Brush.linearGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.1f),
                                        Color.White.copy(alpha = 0.2f),
                                        Color.Transparent
                                    )
                                ),
                                blendMode = BlendMode.Screen
                            )
                        }
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // REWIND text with metallic shine
            Text(
                text = "REWIND",
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 4.sp,
                modifier = Modifier
                    .drawWithCache {
                        onDrawWithContent {
                            drawContent()
                            // Add metallic gradient overlay
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFFFD700).copy(alpha = 0.8f),
                                        Color(0xFFFFEC8B).copy(alpha = 0.9f),
                                        Color.White.copy(alpha = 1f),
                                        Color(0xFFFFD700).copy(alpha = 0.8f)
                                    )
                                ),
                                blendMode = BlendMode.Screen
                            )
                        }
                    }
                    .graphicsLayer {
                        shadowElevation = 30.dp.toPx()
                        rotationZ = -2f
                    }
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Subtitle
            Text(
                text = "Your $year in music",
                fontSize = 20.sp,
                fontWeight = FontWeight.Light,
                color = Color(0xFFFFEC8B),
                letterSpacing = 1.sp,
                modifier = Modifier
                    .alpha(0.9f)
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Username
            Text(
                buildAnnotatedString {
                    withStyle(style = SpanStyle(color = Color.White.copy(alpha = 0.8f))) {
                        append("For ")
                    }
                    withStyle(style = SpanStyle(
                        color = Color(0xFFE6E8FA),
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )) {
                        append(username)
                    }
                },
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(50.dp))
            
            // Swipe hint with animation
            var arrowOffset by remember { mutableStateOf(0f) }
            
            LaunchedEffect(Unit) {
                while (true) {
                    arrowOffset = 0f
                    repeat(50) {
                        arrowOffset = (it / 50f) * 25f
                        delay(16)
                    }
                    delay(500)
                    repeat(50) {
                        arrowOffset = 25f - ((it / 50f) * 25f)
                        delay(16)
                    }
                    delay(1000)
                }
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = arrowOffset.dp)
                        .size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "➤",
                        fontSize = 36.sp,
                        color = Color(0xFFFFD700),
                        modifier = Modifier
                            .drawBehind {
                                // Draw glow behind arrow
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFFFFD700).copy(alpha = 0.5f),
                                            Color.Transparent
                                        )
                                    ),
                                    center = center,
                                    radius = size.minDimension / 1.5f
                                )
                            }
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = "SWIPE TO BEGIN",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 4.sp,
                    color = Color(0xFFB8B8C8),
                    modifier = Modifier.alpha(0.8f)
                )
            }
        }
        
        // Shine overlay effects
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    // Draw radial gradients for shine effect
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x08FFD700),
                                Color(0x00FFD700)
                            )
                        ),
                        center = androidx.compose.ui.geometry.Offset(
                            size.width * 0.2f,
                            size.height * 0.3f
                        ),
                        radius = size.minDimension * 0.3f,
                        blendMode = BlendMode.Screen
                    )
                    
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x06C0C0C0),
                                Color(0x00C0C0C0)
                            )
                        ),
                        center = androidx.compose.ui.geometry.Offset(
                            size.width * 0.8f,
                            size.height * 0.7f
                        ),
                        radius = size.minDimension * 0.4f,
                        blendMode = BlendMode.Screen
                    )
                }
        )
    }
}