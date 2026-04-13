package app.kreate.android.widget

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.Options
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceComposable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.media3.common.util.UnstableApi
import app.kreate.android.R
import app.kreate.android.drawable.APP_ICON_BITMAP
import app.it.fast4x.rimusic.MainActivity
import app.it.fast4x.rimusic.RescueCenterActivity
import app.it.fast4x.rimusic.cleanPrefix
import java.io.File
import androidx.compose.ui.graphics.Color

sealed class Widget : GlanceAppWidget() {

    val songTitleKey = stringPreferencesKey("songTitleKey")
    val songArtistKey = stringPreferencesKey("songArtistKey")
    val isPlayingKey = booleanPreferencesKey("isPlayingKey")
    var bitmapPath = stringPreferencesKey("thumbnailPathKey")

    private var onPlayPauseAction: () -> Unit = {}
    private var onPreviousAction: () -> Unit = {}
    private var onNextAction: () -> Unit = {}

    protected val widgetBackgroundLight = Color(0xFFF4F1EA)
    protected val widgetBackgroundDark = Color(0xFF16171D)
    protected val widgetSurfaceLight = Color(0xFFFFFFFF)
    protected val widgetSurfaceDark = Color(0xFF1F2029)
    protected val widgetAccentLight = Color(0xFFD97706)
    protected val widgetAccentDark = Color(0xFF2EE59D)
    protected val widgetTextLight = Color(0xFF16171D)
    protected val widgetTextDark = Color(0xFFE1E1E2)
    protected val widgetSubtextLight = Color(0xFF6B7280)
    protected val widgetSubtextDark = Color(0xFFA3A4A6)

    @Composable
    protected abstract fun Content(context: Context)

    @Composable
    @GlanceComposable
    protected fun Thumbnail(modifier: GlanceModifier) {
        val bitmap = currentState(bitmapPath)?.let(::decodeWidgetBitmap) ?: APP_ICON_BITMAP
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "cover",
            modifier = modifier.clickable(actionStartActivity<MainActivity>())
        )
    }

    @Composable
    @GlanceComposable
    protected fun Controller() {
        val isPlaying = currentState(isPlayingKey) ?: false

        Row(
            modifier = GlanceModifier.padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.play_skip_back),
                contentDescription = "Previous",
                modifier = GlanceModifier
                    .size(28.dp)
                    .clickable(onPreviousAction)
            )

            Image(
                provider = ImageProvider(
                    if (isPlaying) R.drawable.pause else R.drawable.play
                ),
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = GlanceModifier
                    .size(36.dp)
                    .padding(horizontal = 14.dp)
                    .clickable(onPlayPauseAction)
            )

            Image(
                provider = ImageProvider(R.drawable.play_skip_forward),
                contentDescription = "Next",
                modifier = GlanceModifier
                    .size(28.dp)
                    .clickable(onNextAction)
            )
        }
    }

    @UnstableApi
    suspend fun update(
        context: Context,
        actions: Triple<() -> Unit, () -> Unit, () -> Unit>,
        status: Triple<String, String, Boolean>,
        bitmapFile: File
    ) {
        val glanceId =
            GlanceAppWidgetManager(context).getGlanceIds(this::class.java).firstOrNull() ?: return

        updateAppWidgetState(context, glanceId) {
            it[songTitleKey] = cleanPrefix(status.first)
            it[songArtistKey] = cleanPrefix(status.second)
            it[isPlayingKey] = status.third
            it[bitmapPath] = bitmapFile.absolutePath
        }

        onPlayPauseAction = actions.first
        onPreviousAction = actions.second
        onNextAction = actions.third

        update(context, glanceId)
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme { Content(context) }
        }
    }

    data object Horizontal : Widget() {

        @Composable
        override fun Content(context: Context) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ColorProvider(widgetBackgroundLight, widgetBackgroundDark))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start
            ) {
                Column(
                    modifier = GlanceModifier
                        .background(ColorProvider(widgetSurfaceLight, widgetSurfaceDark))
                        .padding(10.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.Start,
                        modifier = GlanceModifier.fillMaxWidth()
                    ) {
                        Thumbnail(
                            GlanceModifier
                                .background(ColorProvider(Color(0x14D97706), Color(0x142EE59D)))
                                .padding(6.dp)
                                .size(74.dp)
                        )

                        Spacer(modifier = GlanceModifier.width(14.dp))

                        Column(
                            modifier = GlanceModifier,
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = if ((currentState(isPlayingKey) ?: false)) "NOW PLAYING" else "READY",
                                style = TextStyle(
                                    color = ColorProvider(widgetAccentLight, widgetAccentDark),
                                    fontWeight = FontWeight.Medium
                                ),
                                modifier = GlanceModifier.padding(bottom = 4.dp)
                            )

                            Text(
                                text = currentState(songTitleKey).orEmpty().ifBlank { "Cubic Music" },
                                style = TextStyle(
                                    color = ColorProvider(widgetTextLight, widgetTextDark),
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = GlanceModifier.padding(bottom = 2.dp)
                            )

                            Text(
                                text = currentState(songArtistKey).orEmpty().ifBlank { "Tap to reopen your queue" },
                                style = TextStyle(
                                    color = ColorProvider(widgetSubtextLight, widgetSubtextDark)
                                ),
                                modifier = GlanceModifier.padding(bottom = 10.dp)
                            )
                        }
                    }

                    Row(
                        modifier = GlanceModifier
                            .background(ColorProvider(Color(0x16D97706), Color(0x162EE59D)))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Controller()
                    }
                }
            }
        }
    }

    data object Vertical : Widget() {

        @Composable
        override fun Content(context: Context) {
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ColorProvider(widgetBackgroundLight, widgetBackgroundDark))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(ColorProvider(widgetSurfaceLight, widgetSurfaceDark))
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if ((currentState(isPlayingKey) ?: false)) "NOW PLAYING" else "READY",
                        style = TextStyle(
                            color = ColorProvider(widgetAccentLight, widgetAccentDark),
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = GlanceModifier.padding(bottom = 8.dp)
                    )

                    Thumbnail(
                        GlanceModifier
                            .background(ColorProvider(Color(0x14D97706), Color(0x142EE59D)))
                            .padding(6.dp)
                            .size(96.dp)
                    )

                    Spacer(modifier = GlanceModifier.height(12.dp))

                    Text(
                        text = currentState(songTitleKey).orEmpty().ifBlank { "Cubic Music" },
                        style = TextStyle(
                            color = ColorProvider(widgetTextLight, widgetTextDark),
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.padding(bottom = 4.dp)
                    )

                    Text(
                        text = currentState(songArtistKey).orEmpty().ifBlank { "Tap to open your player" },
                        style = TextStyle(
                            color = ColorProvider(widgetSubtextLight, widgetSubtextDark)
                        ),
                        modifier = GlanceModifier.padding(bottom = 10.dp)
                    )

                    Row(
                        modifier = GlanceModifier
                            .background(ColorProvider(Color(0x16D97706), Color(0x162EE59D)))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Controller()
                    }
                }
            }
        }
    }

    data object Rescue : Widget() {

        @Composable
        override fun Content(context: Context) {
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ColorProvider(widgetBackgroundLight, widgetBackgroundDark))
                    .padding(12.dp)
                    .clickable(actionStartActivity<RescueCenterActivity>()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(ColorProvider(widgetSurfaceLight, widgetSurfaceDark))
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.alert_circle),
                        contentDescription = "Rescue Center",
                        modifier = GlanceModifier.size(36.dp)
                    )
                    Spacer(modifier = GlanceModifier.height(10.dp))
                    Text(
                        text = "RESCUE CENTER",
                        style = TextStyle(
                            color = ColorProvider(widgetAccentLight, widgetAccentDark),
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = "Open crash and playback logs",
                        style = TextStyle(
                            color = ColorProvider(widgetTextLight, widgetTextDark),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = "Works even when the main player UI is unhappy",
                        style = TextStyle(
                            color = ColorProvider(widgetSubtextLight, widgetSubtextDark)
                        )
                    )
                }
            }
        }
    }
}

private fun decodeWidgetBitmap(path: String): android.graphics.Bitmap? {
    val bounds = Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val maxDimension = maxOf(bounds.outWidth, bounds.outHeight)
    var sampleSize = 1
    while (maxDimension / sampleSize > 512) {
        sampleSize *= 2
    }

    return BitmapFactory.decodeFile(
        path,
        Options().apply {
            inSampleSize = sampleSize.coerceAtLeast(1)
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
    )
}
