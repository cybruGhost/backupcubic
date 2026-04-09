package app.it.fast4x.rimusic.ui.screens.find

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.it.fast4x.rimusic.LocalPlayerServiceBinder
import app.it.fast4x.rimusic.colorPalette
import app.it.fast4x.rimusic.recognition.AudioRecorder
import app.it.fast4x.rimusic.recognition.RecognizedTrack
import app.it.fast4x.rimusic.recognition.ShazamRepository
import app.it.fast4x.rimusic.typography
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.forcePlay
import app.it.fast4x.rimusic.utils.isDownloadedSong
import app.it.fast4x.rimusic.utils.manageDownload
import app.kreate.android.R
import coil.compose.AsyncImage
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.SearchBody
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.utils.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface FindUiState {
    data object Idle : FindUiState
    data object Listening : FindUiState
    data class Success(val track: RecognizedTrack) : FindUiState
    data class Error(val message: String) : FindUiState
}

@Composable
fun FindScreen(
    onDismiss: () -> Unit,
    onOpenSearch: (String) -> Unit,
    miniPlayer: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val scope = rememberCoroutineScope()
    val recorder = remember { AudioRecorder(scope) }
    val repository = remember { ShazamRepository() }
    val configuration = LocalConfiguration.current
    val sheetMaxHeight = configuration.screenHeightDp.dp * 0.72f

    val duration by recorder.duration.collectAsState(0)
    val recordedBuffer by recorder.buffer.collectAsState(ByteArray(0))
    var state by remember { mutableStateOf<FindUiState>(FindUiState.Idle) }
    var recognitionJob by remember { mutableStateOf<Job?>(null) }
    var lastAttemptSecond by remember { mutableStateOf(0) }
    var isMatching by remember { mutableStateOf(false) }
    var isLoadingSuggestions by remember { mutableStateOf(false) }
    var suggestionsError by remember { mutableStateOf<String?>(null) }
    val suggestions = remember { mutableStateListOf<Innertube.SongItem>() }

    fun stopListening(resetToIdle: Boolean = true) {
        recognitionJob?.cancel()
        recognitionJob = null
        recorder.stop()
        isMatching = false
        lastAttemptSecond = 0
        if (resetToIdle) state = FindUiState.Idle
    }

    fun startListening() {
        if (state is FindUiState.Listening) return
        suggestions.clear()
        suggestionsError = null
        isLoadingSuggestions = false
        state = FindUiState.Listening
        isMatching = false
        lastAttemptSecond = 0
        recorder.start()
        recognitionJob?.cancel()
        recognitionJob = scope.launch {
            delay(12_000L)
            if (state is FindUiState.Listening) {
                recorder.stop()
                isMatching = false
                state = FindUiState.Error("Couldn't identify that song. Try a louder or cleaner sample.")
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            state = FindUiState.Error("Microphone permission is needed for Find.")
        }
    }

    LaunchedEffect(duration, recordedBuffer, state) {
        if (state !is FindUiState.Listening) return@LaunchedEffect
        if (duration < 3 || recordedBuffer.isEmpty()) return@LaunchedEffect
        if (isMatching || duration == lastAttemptSecond || duration - lastAttemptSecond < 2) return@LaunchedEffect

        isMatching = true
        lastAttemptSecond = duration

        scope.launch {
            val track = withContext(Dispatchers.IO) {
                repository.identify(duration, recordedBuffer)
            }
            if (track != null) {
                recorder.stop()
                recognitionJob?.cancel()
                recognitionJob = null
                isMatching = false
                state = FindUiState.Success(track)
            } else if (state is FindUiState.Listening) {
                isMatching = false
            }
        }
    }

    LaunchedEffect(state) {
        val success = state as? FindUiState.Success ?: return@LaunchedEffect
        suggestions.clear()
        isLoadingSuggestions = true
        suggestionsError = null

        val query = listOf(success.track.title, success.track.subtitle)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val result = withContext(Dispatchers.IO) {
            Innertube.searchPage(
                body = SearchBody(
                    query = query,
                    params = ""
                ),
                fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
            )
        }

        val page = result?.getOrNull()
        val matchedSongs = page?.items
            ?.distinctBy { it.key }
            ?.take(5)
            .orEmpty()

        suggestions.clear()
        suggestions.addAll(matchedSongs)
        if (matchedSongs.isEmpty()) {
            suggestionsError = "No playable Cubic suggestions yet for this match."
        }
        isLoadingSuggestions = false
    }

    DisposableEffect(Unit) {
        onDispose {
            recognitionJob?.cancel()
            recorder.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = sheetMaxHeight)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        colorPalette().background1.copy(alpha = 0.98f),
                        colorPalette().background0.copy(alpha = 0.98f)
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(999.dp))
                .background(colorPalette().textDisabled.copy(alpha = 0.35f))
                .padding(horizontal = 24.dp, vertical = 2.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colorPalette().accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.equalizer),
                    contentDescription = "Find",
                    tint = colorPalette().accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Find", style = typography().l, color = colorPalette().text)
                Text(
                    "Listen and identify the song, then play or download it here.",
                    style = typography().xs,
                    color = colorPalette().textSecondary
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = "Close",
                    tint = colorPalette().text
                )
            }
        }

        FindHeroCard(
            state = state,
            duration = duration,
            onStart = {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) startListening() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onStop = { stopListening() }
        )

        val success = state as? FindUiState.Success
        if (success != null) {
            RecognizedTrackCard(
                track = success.track,
                onSearch = {
                    val query = listOf(success.track.title, success.track.subtitle)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                    onOpenSearch(query)
                }
            )

            Text("Best Cubic matches", style = typography().s, color = colorPalette().text)

            when {
                isLoadingSuggestions -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = colorPalette().accent)
                    }
                }

                suggestions.isNotEmpty() -> {
                    suggestions.forEach { item ->
                        val mediaItem = item.asMediaItem
                        val isDownloaded = isDownloadedSong(mediaItem.mediaId)
                        SuggestionCard(
                            item = item,
                            isDownloaded = isDownloaded,
                            onPlay = {
                                binder?.stopRadio()
                                binder?.player?.forcePlay(mediaItem)
                            },
                            onDownload = {
                                manageDownload(context, mediaItem, isDownloaded)
                            }
                        )
                    }
                }

                else -> {
                    Text(
                        suggestionsError ?: "No suggestions available yet.",
                        style = typography().xs,
                        color = colorPalette().textSecondary
                    )
                }
            }
        }

        AnimatedVisibility(visible = state is FindUiState.Listening) {
            Text(
                "Find uses the microphone only while this sheet is actively listening.",
                style = typography().xxs,
                color = colorPalette().textSecondary
            )
        }

        miniPlayer()
    }
}

@Composable
private fun FindHeroCard(
    state: FindUiState,
    duration: Int,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        colorPalette().accent.copy(alpha = 0.18f),
                        colorPalette().background2.copy(alpha = 0.92f)
                    )
                )
            )
            .padding(20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            when (state) {
                FindUiState.Idle -> {
                    Text("Ready to listen", style = typography().m, color = colorPalette().text)
                    Text(
                        "Tap start and hold your phone near the music for a few seconds.",
                        style = typography().xs,
                        color = colorPalette().textSecondary
                    )
                }

                FindUiState.Listening -> {
                    CircularProgressIndicator(color = colorPalette().accent)
                    Text("Listening...", style = typography().m, color = colorPalette().text)
                    Text(
                        "Captured ${duration}s of audio",
                        style = typography().xs,
                        color = colorPalette().textSecondary
                    )
                }

                is FindUiState.Success -> {
                    Text("Song identified", style = typography().m, color = colorPalette().text)
                    Text(
                        "Refining it into playable Cubic matches.",
                        style = typography().xs,
                        color = colorPalette().textSecondary
                    )
                }

                is FindUiState.Error -> {
                    Text("Couldn't match yet", style = typography().m, color = colorPalette().text)
                    Text(
                        state.message,
                        style = typography().xs,
                        color = colorPalette().textSecondary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(containerColor = colorPalette().accent)
                ) {
                    Text(if (state is FindUiState.Listening) "Listening" else "Start")
                }

                if (state is FindUiState.Listening) {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(containerColor = colorPalette().background3)
                    ) {
                        Text("Stop")
                    }
                }
            }
        }
    }
}

@Composable
private fun RecognizedTrackCard(
    track: RecognizedTrack,
    onSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colorPalette().background1.copy(alpha = 0.88f))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.coverUrl ?: track.backgroundUrl,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(90.dp)
                .clip(RoundedCornerShape(20.dp))
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                track.title,
                style = typography().m,
                color = colorPalette().text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.subtitle,
                style = typography().xs,
                color = colorPalette().textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            track.genre?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = typography().xxs, color = colorPalette().accent)
            }
            Button(
                onClick = onSearch,
                colors = ButtonDefaults.buttonColors(containerColor = colorPalette().accent)
            ) {
                Text("More details")
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    item: Innertube.SongItem,
    isDownloaded: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    val song = item.asSong
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colorPalette().background1.copy(alpha = 0.72f))
            .clickable(onClick = onPlay)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(18.dp))
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                song.title,
                style = typography().s,
                color = colorPalette().text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artistsText.orEmpty(),
                style = typography().xs,
                color = colorPalette().textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            song.durationText?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = typography().xxs, color = colorPalette().textSecondary)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(containerColor = colorPalette().accent)
            ) {
                Text("Play")
            }
            Button(
                onClick = onDownload,
                colors = ButtonDefaults.buttonColors(containerColor = colorPalette().background3)
            ) {
                Text(if (isDownloaded) "Remove" else "Download")
            }
        }
    }
}
