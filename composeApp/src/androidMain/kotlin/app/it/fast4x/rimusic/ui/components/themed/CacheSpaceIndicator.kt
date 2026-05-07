package app.it.fast4x.rimusic.ui.components.themed

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.kreate.android.me.knighthat.coil.ImageCacheFactory
import app.it.fast4x.rimusic.LocalPlayerServiceBinder
import app.it.fast4x.rimusic.enums.CacheType
import app.it.fast4x.rimusic.enums.CoilDiskCacheMaxSize
import app.it.fast4x.rimusic.enums.ExoPlayerDiskCacheMaxSize
import app.it.fast4x.rimusic.enums.ExoPlayerDiskDownloadCacheMaxSize
import app.it.fast4x.rimusic.utils.coilDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.rememberPreference


@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun CacheSpaceIndicator(
    cacheType: CacheType = CacheType.Images,
    circularIndicator: Boolean = false,
    horizontalPadding: Dp = 12.dp,
) {

    val coilDiskCacheMaxSize by rememberPreference(
        coilDiskCacheMaxSizeKey,
        CoilDiskCacheMaxSize.`1GB`
    )
    val exoPlayerDiskCacheMaxSize by rememberPreference(
        exoPlayerDiskCacheMaxSizeKey,
        ExoPlayerDiskCacheMaxSize.`2GB`
    )

    val exoPlayerDiskDownloadCacheMaxSize by rememberPreference(
        exoPlayerDiskDownloadCacheMaxSizeKey,
        ExoPlayerDiskDownloadCacheMaxSize.`2GB`
    )

    when (cacheType) {
        CacheType.Images -> {}
        CacheType.CachedSongs -> {
            if (exoPlayerDiskCacheMaxSize == ExoPlayerDiskCacheMaxSize.Unlimited) return
        }
        CacheType.DownloadedSongs -> {
            if (exoPlayerDiskDownloadCacheMaxSize == ExoPlayerDiskDownloadCacheMaxSize.Unlimited) return
        }
    }

    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current

    val imageDiskCacheSize = ImageCacheFactory.getCacheSize()
    val cachedSongsDiskCacheSize = binder?.cache?.cacheSpace
    val downloadedSongsDiskCacheSize = binder?.downloadCache?.cacheSpace

    val progressValue =
        when (cacheType) {
            CacheType.Images -> imageDiskCacheSize.toFloat()
                .div(coilDiskCacheMaxSize.bytes.coerceAtLeast(1))
            CacheType.CachedSongs -> cachedSongsDiskCacheSize?.toFloat()
                ?.div(exoPlayerDiskCacheMaxSize.bytes.coerceAtLeast(1)) ?: 0.0f
            CacheType.DownloadedSongs -> downloadedSongsDiskCacheSize?.toFloat()
                ?.div(exoPlayerDiskDownloadCacheMaxSize.bytes.coerceAtLeast(1)) ?: 0.0f
        }

    if (!circularIndicator)
        ProgressIndicator(
            progress = progressValue,
            strokeCap = StrokeCap.Round,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .padding(horizontal = horizontalPadding)
        )
    else
        ProgressIndicatorCircular(
            progress = progressValue,
            strokeCap = StrokeCap.Round,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .padding(horizontal = horizontalPadding)
        )
}
