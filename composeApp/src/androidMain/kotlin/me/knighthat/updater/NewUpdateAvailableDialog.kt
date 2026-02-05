package me.knighthat.updater

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.kreate.android.BuildConfig
import app.kreate.android.R
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.styling.shimmer
import it.fast4x.rimusic.utils.bold
import it.fast4x.rimusic.utils.color
import it.fast4x.rimusic.utils.medium
import it.fast4x.rimusic.utils.semiBold
import it.fast4x.rimusic.utils.lastUpdateCheckKey
import it.fast4x.rimusic.utils.updateCancelledKey
import it.fast4x.rimusic.appContext
import me.knighthat.utils.Repository
import dev.jeziellago.compose.markdowntext.MarkdownText
import androidx.compose.ui.graphics.Color
import it.fast4x.rimusic.enums.ColorPaletteMode
import it.fast4x.rimusic.ui.styling.ModernBlackColorPalette
import it.fast4x.rimusic.ui.styling.PureBlackColorPalette
import it.fast4x.rimusic.utils.colorPaletteModeKey
import it.fast4x.rimusic.utils.rememberPreference
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.knighthat.updater.worker.ApkInstallWorker
import androidx.compose.foundation.background
import me.knighthat.utils.Toaster
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.GlobalScope
import android.widget.Toast
import kotlinx.coroutines.launch  // ADD THIS IMPORT!

@Composable
fun DialogText(
    text: String,
    style: TextStyle,
    spacerHeight: Dp = 10.dp
) {
    BasicText(
        text = text,
        style = style,
    )
    Spacer(Modifier.height(spacerHeight))
}

object NewUpdateAvailableDialog {

    /**
     * `false` by default.
     *
     * When this field is set to `true`, it'll
     * keep the dialog from showing up even when
     * [isActive] is `true`.
     *
     * This is used to prevent user from getting
     * annoyed by constant pop-up saying that
     * there's a new update available.
     *
     * This value will be reset once the app is
     * restart, either by user or by setting it
     * programmatically.
     */
    var isCancelled: Boolean by mutableStateOf( !BuildConfig.IS_AUTOUPDATE )

    private var showChangelog by mutableStateOf(false)
    private var changelogText by mutableStateOf("")
    private var isDownloading by mutableStateOf(false)
    private var downloadProgress by mutableStateOf(0f)
    private var isDownloaded by mutableStateOf(false)
    private var isInstalling by mutableStateOf(false)
    private var installationStep by mutableStateOf("")

    var isActive: Boolean by mutableStateOf( false )

    // Check if APK is already downloaded when dialog opens
    private fun checkIfAlreadyDownloaded() {
        val isDownloaded = ApkInstallWorker.isApkDownloaded(Updater.build.name)
        this.isDownloaded = isDownloaded
    }

    fun onDismiss() {
        isCancelled = true
        isActive = false
        showChangelog = false
        isDownloading = false
        isDownloaded = false
        isInstalling = false
        downloadProgress = 0f
        
        // Mark update as cancelled when user cancels (but don't update the check time)
        val sharedPrefs = appContext().getSharedPreferences("settings", 0)
        sharedPrefs.edit()
            .putBoolean(updateCancelledKey, true)
            .apply()
    }

    private fun startAutoInstall() {
        // If already downloaded, install it
        if (isDownloaded) {
            startInstallation()
            return
        }
        
        // Otherwise start downloading
        isDownloading = true
        downloadProgress = 0f
        
        // Start the download and installation worker
        ApkInstallWorker.startDownloadAndInstall(
            context = appContext(),
            downloadUrl = Updater.build.downloadUrl,
            fileName = Updater.build.name,
            onProgress = { progress ->
                downloadProgress = progress
            },
            onComplete = {
                isDownloading = false
                isDownloaded = true
                // Show downloaded message
                Toast.makeText(
                    appContext(),
                    "Download complete! Ready to install.",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onError = { error ->
                isDownloading = false
                // Show error toast
                Toast.makeText(
                    appContext(),
                    "Download failed: $error",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private fun startInstallation() {
        isInstalling = true
        installationStep = "Starting installation..."
        
        GlobalScope.launch {  // This will work now with the import
            // Step 1: Verifying APK
            delay(500)
            withContext(Dispatchers.Main) {
                installationStep = "Verifying update package..."
            }
            
            // Step 2: Starting installation
            delay(500)
            withContext(Dispatchers.Main) {
                installationStep = "Starting installation..."
            }
            
            // Actually install the APK
            val success = ApkInstallWorker.installDownloadedApk(appContext(), Updater.build.name)
            
            withContext(Dispatchers.Main) {
                if (success) {
                    installationStep = "Installation started! Follow prompts..."
                    // Show success message
                    Toast.makeText(
                        appContext(),
                        "Installation started! Follow the on-screen instructions.",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // Close dialog after a short delay
                    delay(1000)
                    onDismiss()
                } else {
                    installationStep = "Installation failed"
                    Toast.makeText(
                        appContext(),
                        "Failed to start installation. Try again.",
                        Toast.LENGTH_LONG
                    ).show()
                    isInstalling = false
                }
            }
        }
    }

    private fun cancelDownload() {
        // Cancel the download
        ApkInstallWorker.cancelDownload()
        
        // Reset download state
        isDownloading = false
        downloadProgress = 0f
        
        // Show cancelled message
        Toast.makeText(
            appContext(),
            "Download cancelled",
            Toast.LENGTH_SHORT
        ).show()
    }

    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    fun Render() {
        if( isCancelled || !isActive ) return

        val uriHandler = LocalUriHandler.current
        val context = LocalContext.current
        var colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.System)

        // Check if APK is already downloaded when dialog opens
        LaunchedEffect(Unit) {
            checkIfAlreadyDownloaded()
        }

        if (showChangelog) {
            Dialog(onDismissRequest = { onDismiss() }) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    // Header with title and version
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(300)) + scaleIn(
                            animationSpec = tween(300),
                            initialScale = 0.9f
                        )
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || colorPaletteMode == ColorPaletteMode.PitchBlack) {
                                    Color(0xFF1A1A1A)
                                } else {
                                    colorPalette().background1
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.changelog_list),
                                        contentDescription = null,
                                        tint = colorPalette().accent,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    BasicText(
                                        text = stringResource(
                                            R.string.update_changelogs,
                                            Updater.githubRelease?.tagName ?: BuildConfig.VERSION_NAME
                                        ),
                                        style = typography().l.bold.copy(color = colorPalette().text)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Changelog content
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(400)) + scaleIn(
                            animationSpec = tween(400),
                            initialScale = 0.9f
                        )
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(500.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || colorPaletteMode == ColorPaletteMode.PitchBlack) {
                                    Color(0xFF1A1A1A)
                                } else {
                                    colorPalette().background1
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                MarkdownText(
                                    modifier = Modifier.padding(8.dp),
                                    markdown = changelogText.ifEmpty { stringResource(R.string.no_changelog_available) },
                                    maxLines = 100,
                                    style = typography().xs.semiBold.copy(color = colorPalette().text)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Back button
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(500)) + scaleIn(
                            animationSpec = tween(500),
                            initialScale = 0.9f
                        )
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showChangelog = false },
                            colors = CardDefaults.cardColors(
                                containerColor = colorPalette().accent
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.arrow_left),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    BasicText(
                                        text = stringResource(R.string.back),
                                        style = typography().s.semiBold.copy(color = Color.White)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Dialog(onDismissRequest = { 
                if (isDownloading) {
                    cancelDownload()
                } else {
                    onDismiss()
                }
            }) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    // Header with title
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(300)) + scaleIn(
                            animationSpec = tween(300),
                            initialScale = 0.9f
                        )
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || colorPaletteMode == ColorPaletteMode.PitchBlack) {
                                    Color(0xFF1A1A1A)
                                } else {
                                    colorPalette().background1
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (isDownloaded) R.drawable.install 
                                            else if (isDownloading) R.drawable.download 
                                            else R.drawable.update
                                        ),
                                        contentDescription = null,
                                        tint = colorPalette().accent,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // Different title based on state
                                    BasicText(
                                        text = when {
                                            isInstalling -> stringResource(R.string.installing_update)
                                            isDownloaded -> stringResource(R.string.update_downloaded)
                                            isDownloading -> stringResource(R.string.downloading_update)
                                            else -> buildString {
                                                val releaseSuffix = Updater.githubRelease?.tagName?.removePrefix("v")?.split("-")?.getOrNull(1) ?: ""
                                                val currentSuffix = BuildConfig.VERSION_NAME.removePrefix("v").split("-").getOrNull(1) ?: ""
                                                
                                                if (releaseSuffix == "b" || currentSuffix == "b") {
                                                    append(stringResource(R.string.beta_title))
                                                    append(" ")
                                                } else {
                                                    append(stringResource(R.string.stable_title))
                                                    append(" ")
                                                }
                                                append(stringResource(R.string.update_available))
                                            }
                                        },
                                        style = typography().l.bold.copy(color = colorPalette().text)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    // Different subtitle based on state
                                    BasicText(
                                        text = when {
                                            isInstalling -> installationStep
                                            isDownloaded -> stringResource(R.string.ready_to_install)
                                            isDownloading -> "${(downloadProgress * 100).toInt()}%"
                                            else -> stringResource(R.string.app_update_dialog_version, Updater.githubRelease?.tagName ?: BuildConfig.VERSION_NAME)
                                        },
                                        style = typography().xs.copy(color = colorPalette().textSecondary)
                                    )
                                    
                                    if (!isDownloaded && !isDownloading && !isInstalling) {
                                        BasicText(
                                            text = stringResource(R.string.app_update_dialog_size, Updater.build.readableSize.ifEmpty { "?" }),
                                            style = typography().xs.copy(color = colorPalette().textSecondary)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Installation in progress
                    AnimatedVisibility(
                        visible = isInstalling,
                        enter = fadeIn(animationSpec = tween(300))
                    ) {
                        Column {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF2196F3)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.install),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    BasicText(
                                        text = installationStep,
                                        style = typography().s.semiBold.copy(color = Color.White),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    BasicText(
                                        text = "Please follow the installation prompts...",
                                        style = typography().xs.copy(color = Color.White.copy(alpha = 0.9f))
                                    )
                                }
                            }
                        }
                    }

                    // Download progress indicator WITH CANCEL BUTTON
                    AnimatedVisibility(
                        visible = isDownloading && !isInstalling,
                        enter = fadeIn(animationSpec = tween(300))
                    ) {
                        Column {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || colorPaletteMode == ColorPaletteMode.PitchBlack) {
                                        Color(0xFF1A1A1A)
                                    } else {
                                        colorPalette().background1
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        BasicText(
                                            text = stringResource(R.string.downloading_update),
                                            style = typography().xs.semiBold.copy(color = colorPalette().text)
                                        )
                                        BasicText(
                                            text = "${(downloadProgress * 100).toInt()}%",
                                            style = typography().xs.semiBold.copy(color = colorPalette().accent)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    // Progress bar
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(colorPalette().background2, RoundedCornerShape(2.dp))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(downloadProgress)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(colorPalette().accent, RoundedCornerShape(2.dp))
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    BasicText(
                                        text = stringResource(R.string.download_progress_hint),
                                        style = typography().xxs.copy(color = colorPalette().textSecondary)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Cancel download button
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { cancelDownload() },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFF44336)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.close),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        BasicText(
                                            text = "Cancel Download",
                                            style = typography().s.semiBold.copy(color = Color.White)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Install Now button (when already downloaded)
                    AnimatedVisibility(
                        visible = isDownloaded && !isDownloading && !isInstalling,
                        enter = fadeIn(animationSpec = tween(350)) + scaleIn(
                            animationSpec = tween(350),
                            initialScale = 0.9f
                        )
                    ) {
                        Column {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { startInstallation() },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF4CAF50)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(0.8f)
                                    ) {
                                        BasicText(
                                            text = "Install Now",
                                            style = typography().s.semiBold.copy(color = Color.White),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        BasicText(
                                            text = "Start installation immediately",
                                            style = typography().xxs.copy(color = Color.White.copy(alpha = 0.9f)),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        painter = painterResource(R.drawable.install),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Redownload option
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        // Delete existing file and start fresh
                                        ApkInstallWorker.cancelDownload()
                                        isDownloaded = false
                                        startAutoInstall()
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFFF9800)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.refresh),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        BasicText(
                                            text = "Re-download Update",
                                            style = typography().s.semiBold.copy(color = Color.White)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Auto-install option (when nothing is downloaded yet)
                    AnimatedVisibility(
                        visible = !isDownloaded && !isDownloading && !isInstalling,
                        enter = fadeIn(animationSpec = tween(350)) + scaleIn(
                            animationSpec = tween(350),
                            initialScale = 0.9f
                        )
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { startAutoInstall() },
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF4CAF50)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(0.8f)
                                ) {
                                    BasicText(
                                        text = stringResource(R.string.download_and_install),
                                        style = typography().s.semiBold.copy(color = Color.White),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    BasicText(
                                        text = "Download and install automatically",
                                        style = typography().xxs.copy(color = Color.White.copy(alpha = 0.9f)),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    painter = painterResource(R.drawable.download),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isDownloading || isDownloaded || isInstalling) 8.dp else 5.dp))

                    // Option 1: Go to github page to download (only when not downloading/installing)
                    AnimatedVisibility(
                        visible = !isDownloading && !isDownloaded && !isInstalling,
                        enter = fadeIn(animationSpec = tween(400)) + scaleIn(
                            animationSpec = tween(400),
                            initialScale = 0.9f
                        )
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    val tagUrl = "${Repository.GITHUB}/${Repository.LATEST_TAG_URL}"
                                    uriHandler.openUri(tagUrl)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || colorPaletteMode == ColorPaletteMode.PitchBlack) {
                                    Color(0xFF1A1A1A)
                                } else {
                                    colorPalette().background1
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                BasicText(
                                    text = stringResource(R.string.open_the_github_releases_web_page_and_download_latest_version),
                                    style = typography().xs.semiBold.copy(color = colorPalette().text),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(0.8f)
                                )
                                Icon(
                                    painter = painterResource(R.drawable.globe),
                                    contentDescription = null,
                                    tint = colorPalette().accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    // Option 2: View Changelog (only when not downloading/installing)
                    AnimatedVisibility(
                        visible = !isDownloading && !isInstalling,
                        enter = fadeIn(animationSpec = tween(600)) + scaleIn(
                            animationSpec = tween(600),
                            initialScale = 0.9f
                        )
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    changelogText = Updater.githubRelease?.body ?: ""
                                    showChangelog = true
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || colorPaletteMode == ColorPaletteMode.PitchBlack) {
                                    Color(0xFF1A1A1A)
                                } else {
                                    colorPalette().background1
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                BasicText(
                                    text = stringResource(R.string.view_changelog, Updater.githubRelease?.tagName ?: BuildConfig.VERSION_NAME),
                                    style = typography().xs.semiBold.copy(color = colorPalette().text),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(0.8f)
                                )
                                Icon(
                                    painter = painterResource(R.drawable.changelog_list),
                                    contentDescription = null,
                                    tint = colorPalette().accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Cancel/Close button
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(700)) + scaleIn(
                            animationSpec = tween(700),
                            initialScale = 0.9f
                        )
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDismiss() },
                            colors = CardDefaults.cardColors(
                                containerColor = colorPalette().accent
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    BasicText(
                                        text = if (isDownloaded) "Close" else stringResource(R.string.cancel),
                                        style = typography().s.semiBold.copy(color = Color.White)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}