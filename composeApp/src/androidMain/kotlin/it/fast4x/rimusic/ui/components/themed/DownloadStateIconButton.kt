@file:kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
package it.fast4x.rimusic.ui.components.themed

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import app.kreate.android.R
import it.fast4x.rimusic.utils.getDownloadProgress
import androidx.compose.ui.graphics.drawscope.Stroke

@UnstableApi
@Composable
fun DownloadStateIconButton(
    onClick: () -> Unit,
    onCancelButtonClicked: () -> Unit,
    @DrawableRes icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indication: Indication? = null,
    downloadState: Int,
    mediaId: String? = null
) {
    val progress = if (mediaId != null) getDownloadProgress(mediaId) else 0f

    if (downloadState == Download.STATE_DOWNLOADING
                || downloadState == Download.STATE_QUEUED
                || downloadState == Download.STATE_RESTARTING
                ){
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .clickable(
                    indication = indication ?: ripple(bounded = false, radius = 24.dp),
                    interactionSource = remember { MutableInteractionSource() },
                    enabled = enabled,
                    onClick = onCancelButtonClicked
                )
                .then(modifier),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            if (progress > 0f && downloadState == Download.STATE_DOWNLOADING) {
                CircularWavyProgressIndicator(
                    progress = { progress },
                    color = color,
                    modifier = Modifier.size(18.dp),
                    stroke = Stroke(width = with(androidx.compose.ui.platform.LocalDensity.current) { 2.dp.toPx() }),
                    trackStroke = Stroke(width = with(androidx.compose.ui.platform.LocalDensity.current) { 2.dp.toPx() })
                )
            } else {
                CircularWavyProgressIndicator(
                    color = color,
                    modifier = Modifier.size(18.dp),
                    stroke = Stroke(width = with(androidx.compose.ui.platform.LocalDensity.current) { 2.dp.toPx() }),
                    trackStroke = Stroke(width = with(androidx.compose.ui.platform.LocalDensity.current) { 2.dp.toPx() })
                )
            }
        }
    } else {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = Modifier
                .clickable(
                    indication = indication ?: ripple(bounded = false),
                    interactionSource = remember { MutableInteractionSource() },
                    enabled = enabled,
                    onClick = onClick
                )
                .then(modifier)
        )
    }
}