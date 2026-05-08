package app.it.fast4x.rimusic.utils

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

// ─────────────────────────────────────────────
//  DYNAMIC COLOR SYSTEM  (mirrors AlbumDetails palette extraction)
// ─────────────────────────────────────────────

/**
 * Weather condition → rich dynamic palette, same philosophy as album art color extraction.
 * Each palette carries a dominant, accent, surface, and text color for full theming.
 */
data class WeatherPalette(
    val dominant: Color,          // card backgrounds, hero fills
    val accent: Color,            // highlights, progress bars, badges
    val surface: Color,           // detail card fills
    val onDominant: Color,        // text on dominant
    val onSurface: Color,         // text on surface cards
    val glow: Color               // subtle ambient glow color
)

fun resolveWeatherPalette(condition: String, isNight: Boolean): WeatherPalette = when {
    condition == "thunderstorm" -> WeatherPalette(
        dominant   = Color(0xFF1A1A2E),
        accent     = Color(0xFFE94560),
        surface    = Color(0xFF16213E),
        onDominant = Color(0xFFF0F0F0),
        onSurface  = Color(0xFFCCCCDD),
        glow       = Color(0xFF7B2FBE)
    )
    condition == "rain" || condition == "drizzle" -> WeatherPalette(
        dominant   = Color(0xFF1B2A4A),
        accent     = Color(0xFF4FC3F7),
        surface    = Color(0xFF1E3354),
        onDominant = Color(0xFFE3F2FD),
        onSurface  = Color(0xFFB0C4DE),
        glow       = Color(0xFF1976D2)
    )
    condition == "snow" -> WeatherPalette(
        dominant   = Color(0xFF1F2B3E),
        accent     = Color(0xFFB2EBF2),
        surface    = Color(0xFF263547),
        onDominant = Color(0xFFF0F8FF),
        onSurface  = Color(0xFFCFD8DC),
        glow       = Color(0xFF80DEEA)
    )
    condition == "mist" -> WeatherPalette(
        dominant   = Color(0xFF2D3348),
        accent     = Color(0xFF90A4AE),
        surface    = Color(0xFF37404F),
        onDominant = Color(0xFFECEFF1),
        onSurface  = Color(0xFFB0BEC5),
        glow       = Color(0xFF607D8B)
    )
    condition == "clouds" && isNight -> WeatherPalette(
        dominant   = Color(0xFF1C2231),
        accent     = Color(0xFF9C8FD4),
        surface    = Color(0xFF252B3E),
        onDominant = Color(0xFFE8E8F0),
        onSurface  = Color(0xFFBBBBCC),
        glow       = Color(0xFF5C6BC0)
    )
    condition == "clouds" -> WeatherPalette(
        dominant   = Color(0xFF2C3E50),
        accent     = Color(0xFFFFC107),
        surface    = Color(0xFF34495E),
        onDominant = Color(0xFFF5F5F5),
        onSurface  = Color(0xFFCFD8DC),
        glow       = Color(0xFFFF9800)
    )
    isNight -> WeatherPalette(
        dominant   = Color(0xFF0D1B3E),
        accent     = Color(0xFF64B5F6),
        surface    = Color(0xFF132040),
        onDominant = Color(0xFFE8EAF6),
        onSurface  = Color(0xFFB0BEC5),
        glow       = Color(0xFF1565C0)
    )
    else -> WeatherPalette(  // sunny day
        dominant   = Color(0xFF1A3A5C),
        accent     = Color(0xFFFFD54F),
        surface    = Color(0xFF1E4570),
        onDominant = Color(0xFFFFFDE7),
        onSurface  = Color(0xFFE3F2FD),
        glow       = Color(0xFFF57F17)
    )
}

// ─────────────────────────────────────────────
//  MAIN POPUP
// ─────────────────────────────────────────────

@Composable
fun WeatherForecastPopup(
    weatherData: WeatherData,
    username: String,
    onDismiss: () -> Unit,
    onCityChange: () -> Unit,
    temperatureUnit: String = "celsius"
) {
    var showSportsDialog by remember { mutableStateOf(false) }
    var liveSports by remember { mutableStateOf<List<LiveSport>>(emptyList()) }
    var isLoadingSports by remember { mutableStateOf(false) }
    var selectedLeague by remember { mutableStateOf("eng.1") }

    val isCelsius = temperatureUnit == "celsius"
    val localTime = getAccurateLocalTimeGMT3(weatherData.timezoneOffset)
    val localHour = localTime.get(Calendar.HOUR_OF_DAY)
    val isNight = localHour >= 20 || localHour < 6
    val normalizedCondition = normalizeCondition(weatherData.condition)
    val palette = resolveWeatherPalette(normalizedCondition, isNight)

    val rainProbability = calculateRealRainProbability(
        weatherData.humidity,
        normalizedCondition,
        weatherData.cloudCover,
        weatherData.pressure
    )

    LaunchedEffect(selectedLeague, showSportsDialog) {
        if (showSportsDialog) {
            isLoadingSports = true
            liveSports = fetchLiveSportsFromESPN(selectedLeague)
            isLoadingSports = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.dominant,
        shape = RoundedCornerShape(28.dp),
        title = {
            WeatherDialogHeader(
                username = username,
                isCelsius = isCelsius,
                isLoadingSports = isLoadingSports,
                palette = palette,
                onSportsClick = { showSportsDialog = true },
                onCityChange = onCityChange
            )
        },
        text = {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    HeroWeatherCard(
                        weatherData = weatherData,
                        localTime = localTime,
                        isNight = isNight,
                        normalizedCondition = normalizedCondition,
                        rainProbability = rainProbability,
                        isCelsius = isCelsius,
                        palette = palette
                    )
                }

                if (shouldShowRainPrediction(rainProbability, weatherData.humidity, normalizedCondition)) {
                    item {
                        RainIntelCard(
                            rainProbability = rainProbability,
                            condition = normalizedCondition,
                            humidity = weatherData.humidity,
                            cloudCover = weatherData.cloudCover,
                            pressure = weatherData.pressure,
                            palette = palette
                        )
                    }
                }

                item {
                    InsightCard(
                        weatherData = weatherData,
                        condition = normalizedCondition,
                        rainProbability = rainProbability,
                        localHour = localHour,
                        palette = palette
                    )
                }

                item {
                    ActivityCard(
                        weatherData = weatherData,
                        localHour = localHour,
                        isNight = isNight,
                        rainProbability = rainProbability,
                        normalizedCondition = normalizedCondition,
                        palette = palette
                    )
                }

                item {
                    MetricsGrid(
                        weatherData = weatherData,
                        localHour = localHour,
                        isCelsius = isCelsius,
                        palette = palette
                    )
                }

                item {
                    HydrationBanner(palette = palette)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "Close",
                    color = if (palette.accent.luminance() > 0.4f) Color(0xFF1A1A1A) else Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )

    if (showSportsDialog) {
        LiveSportsDialog(
            sports = liveSports,
            selectedLeague = selectedLeague,
            onLeagueChange = { selectedLeague = it },
            onDismiss = { showSportsDialog = false },
            isLoading = isLoadingSports,
            palette = palette
        )
    }
}

// ─────────────────────────────────────────────
//  DIALOG HEADER
// ─────────────────────────────────────────────

@Composable
private fun WeatherDialogHeader(
    username: String,
    isCelsius: Boolean,
    isLoadingSports: Boolean,
    palette: WeatherPalette,
    onSportsClick: () -> Unit,
    onCityChange: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Hi, $username 👋",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onDominant
            )
            Text(
                text = if (isCelsius) "Showing °C" else "Showing °F",
                style = MaterialTheme.typography.labelSmall,
                color = palette.onDominant.copy(alpha = 0.55f)
            )
        }

        // Sports button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.accent.copy(alpha = 0.18f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onSportsClick() },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isLoadingSports,
                transitionSpec = { fadeIn() togetherWith fadeOut() }
            ) { loading ->
                Text(
                    text = if (loading) "⚙️" else "⚽",
                    fontSize = 18.sp
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // City change button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.accent.copy(alpha = 0.18f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onCityChange() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Change city",
                tint = palette.accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────
//  HERO CARD  — full-bleed cinematic weather header
// ─────────────────────────────────────────────

@Composable
private fun HeroWeatherCard(
    weatherData: WeatherData,
    localTime: Calendar,
    isNight: Boolean,
    normalizedCondition: String,
    rainProbability: Int,
    isCelsius: Boolean,
    palette: WeatherPalette
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(palette.surface, palette.dominant)
                )
            )
            // ambient glow orb
            .drawBehind {
                drawCircle(
                    color = palette.glow.copy(alpha = 0.18f * pulse),
                    radius = size.width * 0.55f,
                    center = Offset(size.width * 0.75f, size.height * 0.2f)
                )
            }
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Date + location row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = weatherData.city,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.onDominant,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatFullDateGMT3(localTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onDominant.copy(alpha = 0.6f)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Emoji + temperature hero
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = getDynamicWeatherEmoji(normalizedCondition, isNight),
                    fontSize = 64.sp,
                    modifier = Modifier.scale(pulse * 0.97f + 0.03f)
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = formatTemperature(weatherData.temp, isCelsius),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.onDominant,
                        lineHeight = 52.sp
                    )
                    Text(
                        text = getAccurateWeatherDescription(normalizedCondition, weatherData),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.accent,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Feels like ${formatTemperature(weatherData.feelsLike, isCelsius)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onDominant.copy(alpha = 0.65f)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Divider line
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(palette.onDominant.copy(alpha = 0.12f))
            )

            Spacer(Modifier.height(14.dp))

            // Greeting + smart summary
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Time badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.accent.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${getTimeOfDayGreeting(localTime.get(Calendar.HOUR_OF_DAY))} • ${formatAccurateLocalTimeGMT3(localTime)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.accent,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = getSmartWeatherAnalysis(weatherData, normalizedCondition, rainProbability),
                style = MaterialTheme.typography.bodySmall,
                color = palette.onDominant.copy(alpha = 0.8f),
                lineHeight = 18.sp
            )
        }
    }
}

// ─────────────────────────────────────────────
//  RAIN INTEL CARD  — animated probability gauge
// ─────────────────────────────────────────────

@Composable
private fun RainIntelCard(
    rainProbability: Int,
    condition: String,
    humidity: Int,
    cloudCover: Int?,
    pressure: Int,
    palette: WeatherPalette
) {
    val animatedProgress by animateFloatAsState(
        targetValue = rainProbability / 100f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "rain_progress"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = getRainEmoji(condition, rainProbability),
                        fontSize = 22.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Precipitation",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.onSurface
                    )
                }
                // Probability badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            when {
                                rainProbability >= 70 -> Color(0xFFE53935).copy(alpha = 0.18f)
                                rainProbability >= 40 -> Color(0xFFFF9800).copy(alpha = 0.18f)
                                else -> Color(0xFF43A047).copy(alpha = 0.18f)
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$rainProbability%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            rainProbability >= 70 -> Color(0xFFEF9A9A)
                            rainProbability >= 40 -> Color(0xFFFFCC80)
                            else -> Color(0xFFA5D6A7)
                        }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Animated progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = when {
                    rainProbability >= 70 -> Color(0xFFEF5350)
                    rainProbability >= 40 -> Color(0xFFFF9800)
                    else -> Color(0xFF66BB6A)
                },
                trackColor = palette.dominant.copy(alpha = 0.5f)
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = getRealRainAnalysis(rainProbability, condition, humidity, cloudCover, pressure),
                style = MaterialTheme.typography.bodySmall,
                color = palette.onSurface.copy(alpha = 0.8f),
                lineHeight = 17.sp
            )
        }
    }
}

// ─────────────────────────────────────────────
//  INSIGHT CARD  — smart forecast intelligence
// ─────────────────────────────────────────────

@Composable
private fun InsightCard(
    weatherData: WeatherData,
    condition: String,
    rainProbability: Int,
    localHour: Int,
    palette: WeatherPalette
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(palette.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🧠", fontSize = 16.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Weather Intelligence",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.accent
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = getSmartForecast(weatherData, condition, rainProbability, localHour),
                style = MaterialTheme.typography.bodySmall,
                color = palette.onSurface.copy(alpha = 0.85f),
                lineHeight = 18.sp
            )
        }
    }
}

// ─────────────────────────────────────────────
//  ACTIVITY CARD  — contextual suggestion
// ─────────────────────────────────────────────

@Composable
private fun ActivityCard(
    weatherData: WeatherData,
    localHour: Int,
    isNight: Boolean,
    rainProbability: Int,
    normalizedCondition: String,
    palette: WeatherPalette
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(palette.glow.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎯", fontSize = 16.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Something you can do",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onSurface
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = getLogicalActivities(weatherData, localHour, isNight, rainProbability, normalizedCondition),
                style = MaterialTheme.typography.bodySmall,
                color = palette.onSurface.copy(alpha = 0.85f),
                lineHeight = 18.sp
            )
        }
    }
}

// ─────────────────────────────────────────────
//  METRICS GRID  — 2×3 data tiles
// ─────────────────────────────────────────────

@Composable
private fun MetricsGrid(
    weatherData: WeatherData,
    localHour: Int,
    isCelsius: Boolean,
    palette: WeatherPalette
) {
    val metrics = listOf(
        MetricItem("💧", "Humidity", "${weatherData.humidity}%", getHumidityAnalysis(weatherData.humidity)),
        MetricItem("☁️", "Cloud Cover", "${getSmartCloudCover(weatherData.cloudCover, weatherData.condition)}%", getCloudCoverAnalysis(getSmartCloudCover(weatherData.cloudCover, weatherData.condition))),
        MetricItem("💨", "Wind", "${weatherData.windSpeed} m/s", getWindAnalysis(weatherData.windSpeed)),
        MetricItem("🌡", "Pressure", "${weatherData.pressure} hPa", getPressureAnalysis(weatherData.pressure)),
        MetricItem("👁", "Visibility", "${weatherData.visibility / 1000} km", getVisibilityAnalysis(weatherData.visibility)),
        MetricItem("🔆", "Min / Max", "${formatTemperature(weatherData.minTemp, isCelsius)} / ${formatTemperature(weatherData.maxTemp, isCelsius)}", getTempRangeAnalysis(weatherData))
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(palette.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📊", fontSize = 16.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Weather Details",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onSurface
                )
            }

            Spacer(Modifier.height(12.dp))

            // 2-column grid of metric tiles
            val chunked = metrics.chunked(2)
            chunked.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { metric ->
                        MetricTile(metric = metric, palette = palette, modifier = Modifier.weight(1f))
                    }
                    // pad last row if odd
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun MetricTile(
    metric: MetricItem,
    palette: WeatherPalette,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(palette.dominant.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(metric.emoji, fontSize = 14.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = metric.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurface.copy(alpha = 0.55f),
                    fontSize = 10.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = metric.value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onDominant,
                fontSize = 15.sp
            )
            Text(
                text = metric.description,
                style = MaterialTheme.typography.labelSmall,
                color = palette.onSurface.copy(alpha = 0.5f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

data class MetricItem(
    val emoji: String,
    val label: String,
    val value: String,
    val description: String
)

// ─────────────────────────────────────────────
//  HYDRATION BANNER
// ─────────────────────────────────────────────

@Composable
private fun HydrationBanner(palette: WeatherPalette) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF1E88E5).copy(alpha = 0.22f),
                        Color(0xFF00ACC1).copy(alpha = 0.12f)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💧", fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Stay Hydrated",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF81D4FA)
                )
                Text(
                    text = "Drink water regularly throughout the day, whatever the weather.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurface.copy(alpha = 0.6f),
                    lineHeight = 15.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
//  LIVE SPORTS DIALOG  — styled with palette
// ─────────────────────────────────────────────

@Composable
private fun LiveSportsDialog(
    sports: List<LiveSport>,
    selectedLeague: String,
    onLeagueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean,
    palette: WeatherPalette
) {
    val leagues = listOf(
        "eng.1"          to "Premier League",
        "esp.1"          to "La Liga",
        "ita.1"          to "Serie A",
        "ger.1"          to "Bundesliga",
        "fra.1"          to "Ligue 1",
        "uefa.champions" to "Champions League"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.dominant,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                text = "⚽  Live Football",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = palette.onDominant
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // League chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(leagues) { (id, name) ->
                        val isSelected = selectedLeague == id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (isSelected) palette.accent
                                    else palette.surface
                                )
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { onLeagueChange(id) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) {
                                    if (palette.accent.luminance() > 0.4f) Color(0xFF1A1A1A) else Color.White
                                } else {
                                    palette.onSurface.copy(alpha = 0.7f)
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(palette.surface)
                                .padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = palette.accent)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Fetching matches...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    sports.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(palette.surface)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🏟", fontSize = 36.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "No matches right now",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.onSurface
                                )
                                Text(
                                    "Check back later for action!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.onSurface.copy(alpha = 0.55f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(sports) { sport ->
                                MatchCard(sport = sport, palette = palette)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Close",
                    color = if (palette.accent.luminance() > 0.4f) Color(0xFF1A1A1A) else Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
private fun MatchCard(sport: LiveSport, palette: WeatherPalette) {
    val isLive = sport.status == "in"
    val isFinal = sport.status == "final"

    val infiniteTransition = rememberInfiniteTransition(label = "live_dot")
    val liveDotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(700), RepeatMode.Reverse
        ),
        label = "live_dot_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .padding(14.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = sport.league,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurface.copy(alpha = 0.5f)
                )

                // Status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            when {
                                isLive  -> Color(0xFFD32F2F).copy(alpha = 0.2f)
                                isFinal -> Color(0xFF2E7D32).copy(alpha = 0.2f)
                                else    -> palette.dominant.copy(alpha = 0.4f)
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLive) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF5350).copy(alpha = liveDotAlpha))
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = when {
                                isLive  -> "LIVE"
                                isFinal -> "FT"
                                else    -> sport.time
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isLive  -> Color(0xFFEF9A9A)
                                isFinal -> Color(0xFFA5D6A7)
                                else    -> palette.onSurface.copy(alpha = 0.7f)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sport.homeTeam,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.onDominant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = sport.awayTeam,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.onDominant
                    )
                }

                if (sport.score.isNotBlank() && sport.status != "scheduled") {
                    Text(
                        text = sport.score,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.accent
                    )
                }
            }

            if (sport.venue.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.dominant.copy(alpha = 0.35f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = sport.venue,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  TEMPERATURE FORMATTING  (unchanged logic)
// ─────────────────────────────────────────────

private fun celsiusToFahrenheit(celsius: Double) = (celsius * 9 / 5) + 32

private fun formatTemperature(temp: Double, isCelsius: Boolean): String {
    val displayTemp = if (isCelsius) temp else celsiusToFahrenheit(temp)
    val unit = if (isCelsius) "°C" else "°F"
    return "${displayTemp.roundToInt()}$unit"
}

// ─────────────────────────────────────────────
//  SPORTS API  (unchanged)
// ─────────────────────────────────────────────

private suspend fun fetchLiveSportsFromESPN(leagueCode: String): List<LiveSport> = withContext(Dispatchers.IO) {
    try {
        val calendar = Calendar.getInstance()
        val endDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        val startDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(calendar.time)

        val apiUrl = "https://site.api.espn.com/apis/site/v2/sports/soccer/$leagueCode/scoreboard?dates=$startDate-$endDate"
        val response = URL(apiUrl).readText()
        val json = JSONObject(response)

        if (!json.has("events")) return@withContext emptyList<LiveSport>()

        val events = json.getJSONArray("events")
        val sports = mutableListOf<LiveSport>()

        for (i in 0 until events.length()) {
            try {
                val event = events.getJSONObject(i)
                val competitions = event.optJSONArray("competitions") ?: continue
                if (competitions.length() == 0) continue

                val competition = competitions.getJSONObject(0)
                val competitors = competition.optJSONArray("competitors") ?: continue
                if (competitors.length() < 2) continue

                val leagueName = competition.optJSONObject("league")?.optString("name") ?: "League"
                val venueName = competition.optJSONObject("venue")?.optString("fullName") ?: "TBD"

                val homeObj = competitors.findJSONObject { it.optString("homeAway") == "home" } ?: competitors.getJSONObject(0)
                val awayObj = competitors.findJSONObject { it.optString("homeAway") == "away" } ?: competitors.getJSONObject(1)

                val homeTeam = homeObj.optJSONObject("team")?.optString("displayName") ?: "Home"
                val awayTeam = awayObj.optJSONObject("team")?.optString("displayName") ?: "Away"

                val homeScore = homeObj.optString("score").ifEmpty { "0" }
                val awayScore = awayObj.optString("score").ifEmpty { "0" }

                val statusObj = event.optJSONObject("status")
                val typeObj = statusObj?.optJSONObject("type")
                val rawStatus = typeObj?.optString("state")?.lowercase() ?: "scheduled"

                val status = when (rawStatus) {
                    "in", "inprogress", "in_progress" -> "in"
                    "post", "final" -> "final"
                    else -> "scheduled"
                }

                val date = event.optString("date", "")
                val matchTime = if (date.isNotBlank()) formatESPNTime(date) else "TBD"

                sports.add(
                    LiveSport(
                        id = event.optString("id", "0"),
                        league = leagueName,
                        homeTeam = homeTeam,
                        awayTeam = awayTeam,
                        score = "$homeScore-$awayScore",
                        status = status,
                        time = matchTime,
                        venue = venueName
                    )
                )
            } catch (e: Exception) { continue }
        }

        sports.sortedWith(compareBy({ it.status != "in" }, { it.status != "scheduled" }, { it.time }))
    } catch (e: Exception) {
        emptyList()
    }
}

private fun JSONArray.findJSONObject(predicate: (JSONObject) -> Boolean): JSONObject? {
    for (i in 0 until length()) {
        val obj = optJSONObject(i) ?: continue
        if (predicate(obj)) return obj
    }
    return null
}

fun formatESPNTime(dateString: String): String = try {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
    val utc = LocalDateTime.parse(dateString, formatter)
    val kenya = utc.atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.of("Africa/Nairobi"))
    kenya.format(DateTimeFormatter.ofPattern("hh:mm a"))
} catch (e: Exception) { dateString }

// ─────────────────────────────────────────────
//  LOGIC HELPERS  (ported from original, unchanged)
// ─────────────────────────────────────────────

private fun getSmartCloudCover(cloudCover: Int?, condition: String): Int = when {
    condition.contains("rain", true) && (cloudCover == null || cloudCover < 70) -> 85
    condition.contains("thunderstorm", true) && (cloudCover == null || cloudCover < 80) -> 95
    condition.contains("drizzle", true) && (cloudCover == null || cloudCover < 60) -> 75
    condition.contains("clear", true) && (cloudCover ?: 0) > 50 -> 20
    condition.contains("cloud", true) && (cloudCover ?: 0) < 40 -> 70
    else -> cloudCover ?: if (condition.contains("clear", true)) 10 else if (condition.contains("cloud", true)) 75 else 50
}

private fun getAccurateLocalTimeGMT3(timezoneOffset: Int): Calendar {
    val calendar = Calendar.getInstance()
    calendar.timeZone = TimeZone.getTimeZone("GMT+3")
    return calendar
}

private fun formatAccurateLocalTimeGMT3(calendar: Calendar): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    formatter.timeZone = calendar.timeZone
    return formatter.format(calendar.time)
}

private fun formatFullDateGMT3(calendar: Calendar): String {
    val formatter = SimpleDateFormat("EEE, d MMM • HH:mm", Locale.getDefault())
    formatter.timeZone = calendar.timeZone
    return formatter.format(calendar.time)
}

private fun getTimeOfDayGreeting(hour: Int) = when {
    hour in 4..11  -> "Good Morning"
    hour in 12..15 -> "Good Afternoon"
    hour in 16..19 -> "Good Evening"
    else           -> "Good Night"
}

private fun calculateRealRainProbability(humidity: Int, condition: String, cloudCover: Int?, pressure: Int): Int {
    val actualCloudCover = getSmartCloudCover(cloudCover, condition)
    return when {
        condition.contains("thunderstorm", true) -> 98
        condition.contains("rain", true) -> if (humidity >= 90) 95 else if (humidity >= 80) 85 else 75
        condition.contains("drizzle", true) -> 70
        else -> minOf(
            (when { humidity >= 90 -> 40; humidity >= 85 -> 35; humidity >= 80 -> 25; humidity >= 70 -> 15; else -> 5 }) +
            (when { actualCloudCover >= 90 -> 35; actualCloudCover >= 80 -> 25; actualCloudCover >= 70 -> 20; actualCloudCover >= 50 -> 10; else -> 5 }) +
            (if (pressure <= 1000) 20 else if (pressure <= 1010) 10 else 0),
            95
        )
    }
}

private fun getRealRainAnalysis(rainProbability: Int, condition: String, humidity: Int, cloudCover: Int?, pressure: Int): String {
    val clouds = getSmartCloudCover(cloudCover, condition)
    val lowPressure = pressure < 1005
    return when {
        condition.contains("thunderstorm", true) -> "Thunderstorm conditions active — strong winds or lightning possible."
        condition.contains("rain", true) && humidity >= 90 -> "Heavy rain expected — high humidity ${humidity}% with dense ${clouds}% cloud cover."
        condition.contains("rain", true) -> "Light to moderate rain — humidity around ${humidity}%."
        condition.contains("drizzle", true) -> "Light drizzle — damp air with humidity ${humidity}%."
        rainProbability >= 70 -> "Rain very likely — ${clouds}% cloud cover and humidity ${humidity}%."
        rainProbability >= 40 -> "Possible showers — ${clouds}% clouds and ${if (lowPressure) "dropping pressure" else "steady conditions"}."
        humidity >= 80 && clouds < 60 -> "High humidity (${humidity}%) but skies remain partly clear."
        clouds >= 70 -> "Mostly cloudy (${clouds}%) but dry conditions expected."
        else -> "Clear and dry — low humidity (${humidity}%) and minimal clouds (${clouds}%)."
    }
}

private fun getAccurateWeatherDescription(condition: String, weatherData: WeatherData) = when (condition) {
    "clear"       -> "Clear skies"
    "clouds"      -> getCloudCoverDescription(getSmartCloudCover(weatherData.cloudCover, weatherData.condition))
    "rain"        -> "${getRainIntensity(weatherData.humidity)} rain"
    "drizzle"     -> "Light drizzle"
    "thunderstorm"-> "Thunderstorm"
    "snow"        -> "Snowing"
    "mist"        -> "Misty"
    else          -> "Clear"
}

private fun getSmartWeatherAnalysis(weatherData: WeatherData, condition: String, rainProbability: Int): String {
    val parts = mutableListOf<String>()
    val temp = weatherData.temp.roundToInt()
    val humidity = weatherData.humidity
    val clouds = getSmartCloudCover(weatherData.cloudCover, condition)

    parts.add(when {
        temp <= 0   -> "Freezing $temp°C ❄️"
        temp in 1..10  -> "Cold $temp°C 🧥"
        temp in 11..18 -> "Cool $temp°C 🌤️"
        temp in 19..27 -> "Warm $temp°C ☀️"
        else           -> "Hot $temp°C 🥵"
    })
    parts.add(when {
        humidity >= 85 -> "Very humid ${humidity}% 💦"
        humidity in 70..84 -> "Humid ${humidity}% 🌫️"
        else -> "Comfortable ${humidity}% 👍"
    })
    val adjusted = if (humidity > 80 && clouds > 60 && rainProbability < 50) rainProbability + 20 else rainProbability
    parts.add(when {
        adjusted >= 70 -> "Rain likely (${adjusted}%) ☔"
        adjusted in 40..69 -> "Possible rain (${adjusted}%) 🌦️"
        else -> "No rain (${adjusted}%) 🌤️"
    })
    return parts.joinToString(" • ")
}

private fun getSmartForecast(weatherData: WeatherData, condition: String, rainProbability: Int, localHour: Int): String {
    val sb = StringBuilder()
    val temp = weatherData.temp
    val humidity = weatherData.humidity
    val clouds = getSmartCloudCover(weatherData.cloudCover, condition)

    when {
        temp <= 5  -> sb.append("Cold air dominates — ideal for staying cozy indoors. ")
        temp in 6.0..17.9 -> sb.append("Cool and calm — keep a layer on. ")
        temp in 18.0..27.9 -> sb.append("Pleasant temperatures — a balanced day ahead. ")
        temp >= 28 -> sb.append("Warm conditions — stay hydrated and limit direct sun exposure. ")
    }
    when {
        humidity >= 90 && condition != "rain" -> sb.append("Air feels heavy and humid though skies remain mostly dry. ")
        humidity in 40..59 -> sb.append("Balanced moisture — comfortable outside. ")
        humidity <= 39 -> sb.append("Dry atmosphere — crisp and refreshing. ")
    }
    when {
        clouds >= 86 -> sb.append("Overcast skies (${clouds}%) blocking most sunlight. ")
        clouds in 31..85 -> sb.append("${getCloudCoverDescription(clouds)} (${clouds}% coverage). ")
        else -> sb.append("Clear blue skies — great light all day. ")
    }
    when {
        rainProbability >= 70 -> sb.append("High chance of rain (${rainProbability}%) — bring an umbrella. ")
        rainProbability in 40..69 -> sb.append("Moderate rain chance (${rainProbability}%) — keep options open. ")
        else -> sb.append("Low rain probability — dry conditions expected. ")
    }
    return sb.trim().toString()
}

private fun getLogicalActivities(weatherData: WeatherData, localHour: Int, isNight: Boolean, rainProbability: Int, normalizedCondition: String): String {
    val temp = weatherData.temp
    val sb = StringBuilder()

    if (normalizedCondition == "rain" || normalizedCondition == "thunderstorm" || rainProbability > 60) {
        sb.append("Rainy weather — perfect for curling up with a book or watching something comforting. Warm tea and quiet thoughts are just right. ")
        if (temp < 15) sb.append("If you head out, bundle up and stay waterproof. 🧥") else sb.append("A waterproof jacket and boots will do fine outside. 🌧️")
    } else {
        when {
            isNight         -> sb.append("A peaceful night — great for a slow city walk, dinner out, or just sitting somewhere quiet and breathing it all in. 🌙")
            localHour in 6..11  -> sb.append("Fresh morning — a walk, a jog, or that first coffee outdoors can set the perfect tone. ☀️")
            localHour in 12..17 -> sb.append("Afternoon is alive — meals outdoors, a swim, some gardening, or just soaking up the warmth. 🌻")
            else            -> sb.append("Evening is setting in — catch a sunset, spend time with people you like, or unwind on a rooftop. 🌇")
        }
        when {
            temp < 10  -> sb.append(" Cold out — wrap up warm. ❄️")
            temp < 20  -> sb.append(" Cool air — maybe carry a light jacket. 🧥")
            temp > 25  -> sb.append(" Warm enough to go light and airy. 👕")
        }
    }
    sb.append(" Whatever the weather — comfort first. 💫")
    return sb.toString()
}

private fun shouldShowRainPrediction(rainProbability: Int, humidity: Int, condition: String) =
    rainProbability > 0 || condition == "rain" || condition == "drizzle" || condition == "thunderstorm" || humidity > 80

private fun normalizeCondition(apiCondition: String): String {
    val c = apiCondition.lowercase(Locale.ROOT)
    return when {
        c.contains("thunderstorm") -> "thunderstorm"
        c.contains("drizzle")      -> "drizzle"
        c.contains("rain")         -> "rain"
        c.contains("snow")         -> "snow"
        c.contains("clear")        -> "clear"
        c.contains("cloud")        -> "clouds"
        c.contains("mist") || c.contains("fog") || c.contains("haze") -> "mist"
        else                       -> "clear"
    }
}

private fun getDynamicWeatherEmoji(condition: String, isNight: Boolean) = when (condition) {
    "clear"       -> if (isNight) "🌙" else "☀️"
    "clouds"      -> if (isNight) "☁️" else "⛅"
    "rain"        -> "🌧️"
    "drizzle"     -> "🌦️"
    "thunderstorm"-> "⛈️"
    "snow"        -> "❄️"
    "mist"        -> "🌫️"
    else          -> if (isNight) "🌙" else "☀️"
}

private fun getRainEmoji(condition: String, rainProbability: Int) = when {
    condition == "thunderstorm" -> "⛈️"
    condition == "rain"         -> "🌧️"
    condition == "drizzle"      -> "🌦️"
    rainProbability >= 70       -> "🌧️"
    rainProbability >= 40       -> "🌦️"
    else                        -> "💧"
}

private fun getHumidityAnalysis(humidity: Int) = when {
    humidity >= 90 -> "Very humid, rain likely"
    humidity >= 80 -> "Humid, possible rain"
    humidity >= 70 -> "Moderate humidity"
    humidity <= 35 -> "Dry and comfortable"
    else           -> "Normal humidity levels"
}

private fun getCloudCoverAnalysis(cloudCover: Int) = when {
    cloudCover >= 86       -> "Overcast skies"
    cloudCover in 71..85   -> "Mostly cloudy"
    cloudCover in 61..70   -> "Partly cloudy"
    cloudCover in 51..60   -> "Broken clouds"
    cloudCover in 41..50   -> "Scattered clouds"
    cloudCover in 31..40   -> "Few clouds"
    else                   -> "Clear skies"
}

private fun getWindAnalysis(windSpeed: Double) = when {
    windSpeed > 8 -> "Strong winds"
    windSpeed > 5 -> "Moderate breeze"
    windSpeed > 2 -> "Light breeze"
    else          -> "Calm conditions"
}

private fun getPressureAnalysis(pressure: Int) = when {
    pressure > 1020 -> "High pressure, stable"
    pressure < 1000 -> "Low pressure, changing"
    else            -> "Normal pressure"
}

private fun getVisibilityAnalysis(visibility: Int) = when {
    visibility > 10000 -> "Excellent"
    visibility > 5000  -> "Good visibility"
    visibility > 2000  -> "Moderate visibility"
    else               -> "Poor visibility"
}

private fun getTempRangeAnalysis(weatherData: WeatherData): String {
    val range = weatherData.maxTemp - weatherData.minTemp
    return when {
        range > 12 -> "Large swing"
        range > 8  -> "Moderate variation"
        range > 4  -> "Small variation"
        else       -> "Stable temps"
    }
}

private fun getRainIntensity(humidity: Int) = when {
    humidity >= 85 -> "Heavy"
    humidity >= 75 -> "Moderate"
    else           -> "Light"
}

private fun getCloudCoverDescription(cloudCover: Int) = when {
    cloudCover >= 86     -> "Overcast"
    cloudCover in 71..85 -> "Mostly cloudy"
    cloudCover in 61..70 -> "Partly cloudy"
    cloudCover in 51..60 -> "Broken clouds"
    cloudCover in 41..50 -> "Scattered clouds"
    cloudCover in 31..40 -> "Few clouds"
    else                 -> "Clear skies"
}

private fun getTextColorForBackground(gradient: Brush): Color = Color.White

// ─────────────────────────────────────────────
//  DATA CLASSES
// ─────────────────────────────────────────────

data class LiveSport(
    val id: String,
    val league: String,
    val homeTeam: String,
    val awayTeam: String,
    val score: String,
    val status: String,
    val time: String,
    val venue: String
)

// WeatherData expected shape:
// data class WeatherData(
//     val city: String,
//     val temp: Double,
//     val feelsLike: Double,
//     val minTemp: Double,
//     val maxTemp: Double,
//     val humidity: Int,
//     val windSpeed: Double,
//     val pressure: Int,
//     val visibility: Int,
//     val condition: String,
//     val timezoneOffset: Int,
//     val cloudCover: Int? = null
// )