package app.kreate.android.themed.rimusic.screen.artist

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.R
import io.ktor.client.call.body
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.BrowseResponse
import it.fast4x.innertube.models.GridRenderer
import it.fast4x.innertube.utils.from
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.Skeleton
import it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import it.fast4x.rimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.fast4x.rimusic.ui.items.VideoItem
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.disableScrollingTextKey
import it.fast4x.rimusic.utils.enqueue
import it.fast4x.rimusic.utils.forcePlay
import it.fast4x.rimusic.utils.playVideo
import it.fast4x.rimusic.utils.preferences
import it.fast4x.rimusic.utils.rememberPreference
import it.fast4x.rimusic.utils.showButtonPlayerVideoKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.knighthat.utils.Toaster

@UnstableApi
@ExperimentalTextApi
@ExperimentalAnimationApi
@ExperimentalMaterial3Api
@Composable
fun ArtistVideos(
    navController: NavController,
    browseId: String,
    params: String?,
    miniPlayer: @Composable () -> Unit
) {
    val lazyListState = rememberLazyListState()
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current

    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
    val isVideoEnabled = context.preferences.getBoolean(showButtonPlayerVideoKey, false)

    var isRefreshing by remember { mutableStateOf(false) }
    val thumbnailHeightDp = 72.dp
    val thumbnailWidthDp = 128.dp

    val videos = remember { mutableStateListOf<Innertube.VideoItem>() }

    suspend fun fetchVideos() {
        val response = runCatching {
            Innertube.browse(browseId = browseId, params = params).body<BrowseResponse>()
        }

        response.fold(
            onSuccess = { browseResponse ->
                val sectionContent = browseResponse.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    ?.firstOrNull()

                // Try musicShelfRenderer first (has duration via fixedColumns, like online search)
                val parsedVideos = sectionContent
                    ?.musicShelfRenderer
                    ?.contents
                    ?.mapNotNull { Innertube.VideoItem.from(it) }
                // Fallback to gridRenderer (no duration available)
                    ?: sectionContent
                        ?.gridRenderer
                        ?.items
                        ?.mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                        ?.mapNotNull(Innertube.VideoItem.Companion::from)

                parsedVideos?.also { videoItems ->
                    val existing = videos.toSet()
                    videos.addAll(videoItems.filterNot { it in existing })
                }
            },
            onFailure = { Toaster.e(R.string.an_error_has_occurred) }
        )
    }

    LaunchedEffect(Unit) {
        fetchVideos()
        isRefreshing = false
    }

    Skeleton(
        navController = navController,
        miniPlayer = miniPlayer,
        navBarContent = {}
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                CoroutineScope(Dispatchers.IO).launch {
                    fetchVideos()
                    isRefreshing = false
                }
            }
        ) {
            LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer),
                modifier = Modifier.background(colorPalette().background0)
            ) {
                items(
                    items = videos.distinctBy(Innertube.VideoItem::key),
                    key = Innertube.VideoItem::key
                ) { video ->
                    SwipeablePlaylistItem(
                        mediaItem = video.asMediaItem,
                        onPlayNext = {
                            binder?.player?.addNext(video.asMediaItem)
                        },
                        onDownload = {
                            Toaster.w(R.string.downloading_videos_not_supported)
                        },
                        onEnqueue = {
                            binder?.player?.enqueue(video.asMediaItem)
                        }
                    ) {
                        VideoItem(
                            video = video,
                            thumbnailWidthDp = thumbnailWidthDp,
                            thumbnailHeightDp = thumbnailHeightDp,
                            modifier = Modifier
                                .background(colorPalette().background0)
                                .combinedClickable(
                                    onLongClick = {
                                        menuState.display {
                                            NonQueuedMediaItemMenu(
                                                navController = navController,
                                                mediaItem = video.asMediaItem,
                                                onDismiss = menuState::hide,
                                                disableScrollingText = disableScrollingText
                                            )
                                        }
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.LongPress
                                        )
                                    },
                                    onClick = {
                                        binder?.stopRadio()
                                        if (isVideoEnabled)
                                            binder?.player?.playVideo(video.asMediaItem)
                                        else
                                            binder?.player?.forcePlay(video.asMediaItem)
                                    }
                                ),
                            disableScrollingText = disableScrollingText
                        )
                    }
                }
            }
        }
    }
}