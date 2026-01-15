@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.media3.common.util.UnstableApi::class
)

package it.fast4x.rimusic.ui.screens.settings

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import app.kreate.android.R
import io.ktor.http.Url
import it.fast4x.compose.persist.persistList
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.utils.parseCookieString
import it.fast4x.piped.Piped
import it.fast4x.piped.models.Instance
import it.fast4x.piped.models.Session
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.ThumbnailRoundness
import it.fast4x.rimusic.extensions.discord.DiscordLoginAndGetToken
import it.fast4x.rimusic.extensions.youtubelogin.YouTubeLogin
import it.fast4x.rimusic.extensions.youtubelogin.YoutubeSessionManager
import it.fast4x.rimusic.thumbnailShape
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.CustomModalBottomSheet
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import it.fast4x.rimusic.ui.components.themed.Menu
import it.fast4x.rimusic.ui.components.themed.MenuEntry
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import it.fast4x.rimusic.utils.enableYouTubeLoginKey
import it.fast4x.rimusic.utils.enableYouTubeSyncKey
import it.fast4x.rimusic.utils.isAtLeastAndroid7
import it.fast4x.rimusic.utils.isDiscordPresenceEnabledKey
import it.fast4x.rimusic.utils.isPipedCustomEnabledKey
import it.fast4x.rimusic.utils.isPipedEnabledKey
import it.fast4x.rimusic.utils.pipedApiBaseUrlKey
import it.fast4x.rimusic.utils.pipedApiTokenKey
import it.fast4x.rimusic.utils.pipedInstanceNameKey
import it.fast4x.rimusic.utils.pipedPasswordKey
import it.fast4x.rimusic.utils.pipedUsernameKey
import it.fast4x.rimusic.utils.preferences
import it.fast4x.rimusic.utils.rememberEncryptedPreference
import it.fast4x.rimusic.utils.rememberPreference
import it.fast4x.rimusic.utils.restartActivityKey
import it.fast4x.rimusic.utils.thumbnailRoundnessKey
import it.fast4x.rimusic.utils.ytAccountChannelHandleKey
import it.fast4x.rimusic.utils.ytAccountEmailKey
import it.fast4x.rimusic.utils.ytAccountNameKey
import it.fast4x.rimusic.utils.ytAccountThumbnailKey
import it.fast4x.rimusic.utils.ytCookieKey
import it.fast4x.rimusic.utils.ytDataSyncIdKey
import it.fast4x.rimusic.utils.ytVisitorDataKey
import kotlinx.coroutines.launch
import me.knighthat.coil.ImageCacheFactory
import me.knighthat.component.dialog.RestartAppDialog
import timber.log.Timber



@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@SuppressLint("BatteryLife")
@Composable
fun AccountsSettings() {
    val context = LocalContext.current
    val thumbnailRoundness by rememberPreference(
        thumbnailRoundnessKey,
        ThumbnailRoundness.Heavy
    )

    var restartActivity by rememberPreference(restartActivityKey, false)
    var restartService by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth(
                if (NavigationBarPosition.Right.isCurrent())
                    Dimensions.contentWidthRightBar
                else 1f
            )
            .verticalScroll(rememberScrollState())
    ) {
        HeaderWithIcon(
            title = stringResource(R.string.tab_accounts),
            iconId = R.drawable.person,
            enabled = false,
            showIcon = true,
            modifier = Modifier,
            onClick = {}
        )

        // Settings Description
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.accounts_settings_description),
                color = colorPalette().textSecondary,
                style = typography().s,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // YouTube Music Section
        YouTubeMusicAccountSection(
            restartService = restartService,
            onRestartService = { restartService = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Piped Section
        if (isAtLeastAndroid7) {
            PipedAccountSection()
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Discord Section
        if (isAtLeastAndroid7) {
            DiscordAccountSection()
        }

        Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
    }
}

// Helper composables for settings items
@Composable
private fun SettingItem(
    title: String,
    text: String = "",
    icon: Int? = null,
    onClick: (() -> Unit)? = null,
    isChecked: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = colorPalette()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 16.dp),
                    tint = colors.text
                )
            }
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = colors.text,
                    style = typography().m,
                    fontWeight = FontWeight.Medium
                )
                
                if (text.isNotEmpty()) {
                    Text(
                        text = text,
                        color = colors.textSecondary,
                        style = typography().s,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        
        onClick?.let {
            androidx.compose.material3.IconButton(
                onClick = it,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.chevron_forward),
                    contentDescription = null,
                    tint = colors.textSecondary
                )
            }
        }
        
        onCheckedChange?.let {
            androidx.compose.material3.Switch(
                checked = isChecked,
                onCheckedChange = it
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: Int? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(colorPalette().background1, thumbnailShape())
    ) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 8.dp),
                    tint = colorPalette().text
                )
            }
            
            Text(
                text = title,
                color = colorPalette().text,
                style = typography().m,
                fontWeight = FontWeight.Bold
            )
        }
        
        // Divider
        androidx.compose.material3.Divider(
            color = colorPalette().background2,
            thickness = 1.dp
        )
        
        // Content
        content()
    }
}

@Composable
private fun YouTubeMusicAccountSection(
    restartService: Boolean,
    onRestartService: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val thumbnailRoundness by rememberPreference(
        thumbnailRoundnessKey,
        ThumbnailRoundness.Heavy
    )
    val scope = rememberCoroutineScope()

    var isYouTubeLoginEnabled by rememberPreference(enableYouTubeLoginKey, false)
    var isYouTubeSyncEnabled by rememberPreference(enableYouTubeSyncKey, false)
    var loginYouTube by remember { mutableStateOf(false) }
    var isSwitchingAccount by remember { mutableStateOf(false) }
    var visitorData by rememberPreference(key = ytVisitorDataKey, defaultValue = Innertube.DEFAULT_VISITOR_DATA)
    var dataSyncId by rememberPreference(key = ytDataSyncIdKey, defaultValue = "")
    var cookie by rememberPreference(key = ytCookieKey, defaultValue = "")
    var accountName by rememberPreference(key = ytAccountNameKey, defaultValue = "")
    var accountEmail by rememberPreference(key = ytAccountEmailKey, defaultValue = "")
    var accountChannelHandle by rememberPreference(
        key = ytAccountChannelHandleKey,
        defaultValue = ""
    )
    var accountThumbnail by rememberPreference(key = ytAccountThumbnailKey, defaultValue = "")
    var isLoadingAccountInfo by remember { mutableStateOf(false) }
    var accountInfoError by remember { mutableStateOf<String?>(null) }
    
    // Check if logged in (has SAPISID cookie)
    val isLoggedIn = remember(cookie) {
        val hasSAPISID = "SAPISID" in parseCookieString(cookie)
        Timber.d("isLoggedIn check: cookie length=${cookie.length}, hasSAPISID=$hasSAPISID")
        hasSAPISID
    }
    
    // Check if we have account info - check multiple fields
    val hasAccountInfo = remember(accountName, accountEmail, accountThumbnail) {
        val hasInfo = accountName.isNotEmpty() || accountEmail.isNotEmpty() || accountThumbnail.isNotEmpty()
        Timber.d("hasAccountInfo check: name='$accountName', email='$accountEmail', thumbnail='$accountThumbnail', hasInfo=$hasInfo")
        hasInfo
    }

    // Enhanced function to fetch account info with retry logic
    suspend fun fetchAccountInfo(): Boolean {
        try {
            Timber.d("Starting account info fetch...")
            isLoadingAccountInfo = true
            accountInfoError = null
            
            // Check if we have a valid cookie first
            if (!cookie.contains("SAPISID")) {
                accountInfoError = "No valid login session found"
                Timber.d("No SAPISID cookie, cannot fetch account info")
                return false
            }
            
            // Try to get from API with retry
            var accountInfo: it.fast4x.innertube.models.AccountInfo? = null
            var retryCount = 0
            val maxRetries = 2
            
            while (accountInfo == null && retryCount < maxRetries) {
                try {
                    Timber.d("Attempting account info fetch (attempt ${retryCount + 1})...")
                    accountInfo = Innertube.accountInfo().getOrNull()
                    
                    if (accountInfo == null && retryCount < maxRetries - 1) {
                        // Wait before retry
                        kotlinx.coroutines.delay(1000L * (retryCount + 1))
                    }
                } catch (e: Exception) {
                    Timber.e("Attempt ${retryCount + 1} failed: ${e.message}")
                    if (retryCount < maxRetries - 1) {
                        kotlinx.coroutines.delay(1000L * (retryCount + 1))
                    }
                }
                retryCount++
            }
            
            Timber.d("Final API account info result: $accountInfo")
            
            if (accountInfo != null) {
                accountName = accountInfo.name.orEmpty()
                accountEmail = accountInfo.email.orEmpty()
                accountChannelHandle = accountInfo.channelHandle.orEmpty()
                accountThumbnail = accountInfo.thumbnailUrl.orEmpty()
                
                // Update session
                val session = YoutubeSessionManager.createSessionFromPreferences(
                    cookie = cookie,
                    visitorData = visitorData,
                    dataSyncId = dataSyncId,
                    accountName = accountName,
                    accountEmail = accountEmail,
                    accountChannelHandle = accountChannelHandle,
                    accountThumbnail = accountThumbnail
                )
                YoutubeSessionManager.updateSession(session)
                
                Timber.d("Successfully fetched account info: $accountName")
                return true
            } else {
                Timber.w("No account info returned from API after $maxRetries attempts")
                
                // Try to extract basic info from cookie if possible
                if (cookie.contains("SAPISID")) {
                    // We're logged in but couldn't get full account info
                    // Check if we can extract email from cookie
                    val emailMatch = Regex("email=([^;]+)").find(cookie)
                    val nameMatch = Regex("name=([^;]+)").find(cookie)
                    
                    if (emailMatch != null || nameMatch != null) {
                        val extractedName = nameMatch?.groupValues?.get(1) ?: "YouTube User"
                        val extractedEmail = emailMatch?.groupValues?.get(1) ?: ""
                        
                        accountName = extractedName
                        accountEmail = extractedEmail
                        
                        Timber.d("Extracted basic info from cookie: $extractedName")
                        accountInfoError = "Limited account info available"
                        return true
                    }
                }
                
                accountInfoError = "Could not retrieve account information. Please try refreshing."
                return false
            }
        } catch (e: Exception) {
            Timber.e("Error fetching account info: ${e.message}")
            accountInfoError = "Failed to fetch account info: ${e.message}"
            return false
        } finally {
            isLoadingAccountInfo = false
        }
    }

    // Try to fetch account info when we have SAPISID but no account info
    LaunchedEffect(isLoggedIn, hasAccountInfo) {
        if (isLoggedIn && !hasAccountInfo && !isLoadingAccountInfo) {
            Timber.d("Auto-fetching account info: loggedIn=$isLoggedIn, hasAccountInfo=$hasAccountInfo")
            scope.launch {
                fetchAccountInfo()
            }
        }
    }

    // Also try to fetch when cookie changes to SAPISID
    LaunchedEffect(cookie) {
        if (cookie.contains("SAPISID") && !hasAccountInfo && !isLoadingAccountInfo) {
            Timber.d("Cookie changed, attempting to fetch account info")
            scope.launch {
                fetchAccountInfo()
            }
        }
    }
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(600)) + scaleIn(
            animationSpec = tween(600),
            initialScale = 0.9f
        )
    ) {
        SectionCard(
            title = "YOUTUBE MUSIC",
            icon = R.drawable.ytmusic
        ) {
            SettingItem(
                title = stringResource(R.string.enable_youtube_music_login),
                text = "",
                icon = R.drawable.ytmusic,
                isChecked = isYouTubeLoginEnabled,
                onCheckedChange = {
                    isYouTubeLoginEnabled = it
                    if (!it) {
                        // Clear all data when disabled
                        visitorData = Innertube.DEFAULT_VISITOR_DATA
                        dataSyncId = ""
                        cookie = ""
                        accountName = ""
                        accountChannelHandle = ""
                        accountEmail = ""
                        accountThumbnail = ""
                        accountInfoError = null
                        YoutubeSessionManager.clearSession()
                        Timber.d("YouTube login disabled, all data cleared")
                    }
                }
            )

            AnimatedVisibility(visible = isYouTubeLoginEnabled) {
                Column {
                    // Show loading when fetching account info
                    if (isLoadingAccountInfo) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Fetching account info...", style = typography().s, color = colorPalette().textSecondary)
                            }
                        }
                    }
                    
                    // Show error if any
                    accountInfoError?.let { error ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = error,
                                    color = androidx.compose.ui.graphics.Color.Red,
                                    style = typography().s
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        scope.launch {
                                            fetchAccountInfo()
                                        }
                                    }
                                ) {
                                    Text("Retry", style = typography().s)
                                }
                            }
                        }
                    }
                    
            // Account Info Display
            if (isLoggedIn) {
                if (hasAccountInfo || accountName.isNotEmpty() || accountEmail.isNotEmpty()) {
                    // Show account info display with actual or placeholder data
                    AccountInfoDisplay(
                        accountThumbnail = accountThumbnail,
                        accountName = if (accountName.isNotEmpty()) accountName else "YouTube Music User",
                        accountEmail = if (accountEmail.isNotEmpty()) accountEmail else "Logged in",
                        accountChannelHandle = accountChannelHandle,
                        onSwitchAccount = {
                            isSwitchingAccount = true
                            loginYouTube = true
                            accountInfoError = null
                        },
                        onRefreshAccount = {
                            scope.launch {
                                if (fetchAccountInfo()) {
                                    android.widget.Toast.makeText(context, "Account info refreshed", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    android.widget.Toast.makeText(context, "Failed to refresh account info", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                } else if (!isLoadingAccountInfo && accountInfoError == null) {
                    // Show loading/retry UI for fetching account info
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Fetching account information...",
                                color = colorPalette().textSecondary,
                                style = typography().s
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    scope.launch {
                                        fetchAccountInfo()
                                    }
                                }
                            ) {
                                Text("Try Now", style = typography().s)
                            }
                        }
                    }
                }
            }
                    // Connect/Disconnect/Switch Account Button
                    SettingItem(
                        title = when {
                            isLoggedIn && hasAccountInfo -> "Switch Account"
                            isLoggedIn && !hasAccountInfo -> "Disconnect"
                            else -> stringResource(R.string.youtube_connect)
                        },
                        text = when {
                            isLoggedIn && hasAccountInfo -> "Logged in as $accountName"
                            isLoggedIn && !hasAccountInfo -> "Logged in (no account info)"
                            else -> "Not logged in"
                        },
                        icon = when {
                            isLoggedIn && hasAccountInfo -> R.drawable.switch_account
                            isLoggedIn && !hasAccountInfo -> R.drawable.logout
                            else -> R.drawable.person
                        },
                       onClick = {
                        if (isLoggedIn) {
                            if (hasAccountInfo) {
                                // Switch account - show login modal
                                isSwitchingAccount = true
                                loginYouTube = true
                                accountInfoError = null
                            } else {
                                // Logout (disconnect) - clear EVERYTHING
                                // Clear preferences
                                cookie = ""
                                accountName = ""
                                accountChannelHandle = ""
                                accountEmail = ""
                                accountThumbnail = ""
                                visitorData = Innertube.DEFAULT_VISITOR_DATA
                                dataSyncId = ""
                                accountInfoError = null
                                
                                // Clear web storage
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.removeAllCookies(null)
                                cookieManager.flush()
                                WebStorage.getInstance().deleteAllData()
                                
                                // Clear session
                                YoutubeSessionManager.clearSession()
                                
                                android.widget.Toast.makeText(context, "Logged out", android.widget.Toast.LENGTH_SHORT).show()
                                onRestartService(true)
                            }
                        } else {
                            // Login (first time)
                            isSwitchingAccount = false
                            loginYouTube = true
                        }
                    }
                    )

                    // Login/Switch Account Modal
                    if (loginYouTube) {
                        LoginYouTubeModal(
                            loginYouTube = loginYouTube,
                            isSwitchingAccount = isSwitchingAccount,
                            onDismiss = { 
                                loginYouTube = false
                                isSwitchingAccount = false
                            },
                            thumbnailRoundness = thumbnailRoundness,
                         onLoginSuccess = { cookieRetrieved, name, email, channel, thumbnail ->
                    loginYouTube = false
                    isSwitchingAccount = false
                    
                    Timber.d("onLoginSuccess called: cookie contains SAPISID=${cookieRetrieved.contains("SAPISID")}")
                    Timber.d("Switching account mode: $isSwitchingAccount")
                    
                    if (cookieRetrieved.contains("SAPISID")) {
                        cookie = cookieRetrieved
                        
                        // IMPORTANT: When switching accounts, we get new account info
                        // When not switching, we might get empty strings but that's OK
                        if (isSwitchingAccount) {
                            // Clear old account info when switching
                            accountName = ""
                            accountEmail = ""
                            accountChannelHandle = ""
                            accountThumbnail = ""
                            
                            // Only set if we got new info
                            if (name.isNotEmpty()) accountName = name
                            if (email.isNotEmpty()) accountEmail = email
                            if (channel.isNotEmpty()) accountChannelHandle = channel
                            if (thumbnail.isNotEmpty()) accountThumbnail = thumbnail
                        } else {
                            // Regular login - set what we got
                            accountName = name
                            accountEmail = email
                            accountChannelHandle = channel
                            accountThumbnail = thumbnail
                        }
                        
                        // Update session
                        val session = YoutubeSessionManager.createSessionFromPreferences(
                            cookie = cookieRetrieved,
                            visitorData = visitorData,
                            dataSyncId = dataSyncId,
                            accountName = accountName,
                            accountEmail = accountEmail,
                            accountChannelHandle = accountChannelHandle,
                            accountThumbnail = accountThumbnail
                        )
                        YoutubeSessionManager.updateSession(session)
                        
                        // Show appropriate message
                        val message = if (isSwitchingAccount) {
                            if (name.isNotEmpty()) "Switched to $name" 
                            else "Account switched successfully"
                        } else {
                            if (name.isNotEmpty()) "Logged in as $name" 
                            else "Logged in successfully"
                        }
                        
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                        
                        // Try to fetch account info in background (especially if we got empty data)
                        scope.launch {
                            fetchAccountInfo()
                        }
                        
                        onRestartService(true)
                    } else {
                        accountInfoError = "Login failed: No SAPISID cookie found"
                    }
                }
                        )
                    }

                    // Sync Option - Only show if logged in with account info
                    AnimatedVisibility(visible = isLoggedIn && hasAccountInfo) {
                        SettingItem(
                            title = "Sync data with YTM account",
                            text = "Playlists, albums, artists, history, likes, etc.",
                            icon = R.drawable.sync,
                            isChecked = isYouTubeSyncEnabled,
                            onCheckedChange = { 
                                isYouTubeSyncEnabled = it
                                if (it) {
                                    // When enabling sync, ensure we have proper account info
                                    if (!hasAccountInfo && isLoggedIn) {
                                        scope.launch {
                                            fetchAccountInfo()
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountInfoDisplay(
    accountThumbnail: String,
    accountName: String,
    accountEmail: String,
    accountChannelHandle: String,
    onSwitchAccount: () -> Unit,
    onRefreshAccount: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(colorPalette().background2, thumbnailShape())
            .padding(16.dp)
    ) {
        Text(
            text = "YouTube Account",
            color = colorPalette().text,
            fontWeight = FontWeight.Bold,
            style = typography().m,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (accountThumbnail.isNotEmpty()) {
                ImageCacheFactory.AsyncImage(
                    thumbnailUrl = accountThumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(thumbnailShape())
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.person),
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(thumbnailShape()),
                    tint = colorPalette().textSecondary
                )
            }
            
            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            ) {
                Text(
                    text = accountName,
                    color = colorPalette().text,
                    fontWeight = FontWeight.Medium,
                    style = typography().m
                )
                
                if (accountChannelHandle.isNotEmpty()) {
                    Text(
                        text = accountChannelHandle,
                        color = colorPalette().textSecondary,
                        style = typography().s,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                
                if (accountEmail.isNotEmpty()) {
                    Text(
                        text = accountEmail,
                        color = colorPalette().textSecondary,
                        style = typography().s,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            
            // Switch Account Button
            androidx.compose.material3.IconButton(
                onClick = onSwitchAccount
            ) {
                Icon(
                    painter = painterResource(R.drawable.switch_account),
                    contentDescription = "Switch Account",
                    tint = colorPalette().textSecondary
                )
            }
        }
        
        // Action Buttons
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            androidx.compose.material3.TextButton(
                onClick = onRefreshAccount
            ) {
                Icon(
                    painter = painterResource(R.drawable.refresh),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colorPalette().textSecondary
                )
                Text(
                    text = "Refresh",
                    color = colorPalette().textSecondary,
                    style = typography().s,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            
            androidx.compose.material3.TextButton(
                onClick = onSwitchAccount
            ) {
                Icon(
                    painter = painterResource(R.drawable.switch_account),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colorPalette().textSecondary
                )
                Text(
                    text = "Switch Account",
                    color = colorPalette().textSecondary,
                    style = typography().s,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun LoginYouTubeModal(
    loginYouTube: Boolean,
    isSwitchingAccount: Boolean,
    onDismiss: () -> Unit,
    thumbnailRoundness: ThumbnailRoundness,
    onLoginSuccess: (String, String, String, String, String) -> Unit
) {
    CustomModalBottomSheet(
        showSheet = loginYouTube,
        onDismissRequest = onDismiss,
        containerColor = colorPalette().background0,
        contentColor = colorPalette().background0,
        modifier = Modifier.fillMaxWidth(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 0.dp),
                color = colorPalette().background0,
                shape = thumbnailShape()
            ) {}
        },
        shape = thumbnailRoundness.shape
    ) {
        YouTubeLogin(
            onLogin = onLoginSuccess,
            isSwitchingAccount = isSwitchingAccount
        )
    }
}

// Keep the rest of the file as is (Piped and Discord sections remain unchanged)
@Composable
private fun PipedAccountSection() {
    val context = LocalContext.current
    
    var isPipedEnabled by rememberPreference(isPipedEnabledKey, false)
    var isPipedCustomEnabled by rememberPreference(isPipedCustomEnabledKey, false)
    var pipedUsername by rememberEncryptedPreference(pipedUsernameKey, "")
    var pipedPassword by rememberEncryptedPreference(pipedPasswordKey, "")
    var pipedInstanceName by rememberEncryptedPreference(pipedInstanceNameKey, "")
    var pipedApiBaseUrl by rememberEncryptedPreference(pipedApiBaseUrlKey, "")
    var pipedApiToken by rememberEncryptedPreference(pipedApiTokenKey, "")
    
    var loadInstances by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var instances by persistList<Instance>(tag = "otherSettings/pipedInstances")
    var noInstances by remember { mutableStateOf(false) }
    var executeLogin by remember { mutableStateOf(false) }
    var showInstances by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<Result<Session>?>(null) }
    
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()
    
    // Check if Piped is connected
    val isPipedConnected = remember(pipedApiToken) {
        pipedApiToken.isNotEmpty()
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(800)) + scaleIn(
            animationSpec = tween(800),
            initialScale = 0.9f
        )
    ) {
        SectionCard(
            title = stringResource(R.string.piped_account),
            icon = R.drawable.piped_logo
        ) {
            // Loading Dialog
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            SettingItem(
                title = stringResource(R.string.enable_piped_syncronization),
                text = "",
                icon = R.drawable.piped_logo,
                isChecked = isPipedEnabled,
                onCheckedChange = { isPipedEnabled = it }
            )

            AnimatedVisibility(visible = isPipedEnabled) {
                Column {
                    // Account Info Display
                    if (isPipedConnected) {
                        PipedAccountInfoDisplay(
                            instanceName = pipedInstanceName,
                            username = pipedUsername
                        )
                    }
                    
                    SettingItem(
                        title = stringResource(R.string.piped_custom_instance),
                        text = "",
                        icon = R.drawable.server,
                        isChecked = isPipedCustomEnabled,
                        onCheckedChange = { isPipedCustomEnabled = it }
                    )
                    
                    AnimatedVisibility(visible = isPipedCustomEnabled) {
                        Column {
                            var showCustomInstanceDialog by remember { mutableStateOf(false) }
                            SettingItem(
                                title = stringResource(R.string.piped_custom_instance),
                                text = pipedApiBaseUrl,
                                icon = R.drawable.server,
                                onClick = { showCustomInstanceDialog = true }
                            )
                        }
                    }
                    
                    AnimatedVisibility(visible = !isPipedCustomEnabled) {
                        SettingItem(
                            title = stringResource(R.string.piped_change_instance),
                            text = pipedInstanceName,
                            icon = R.drawable.open,
                            onClick = {
                                loadInstances = true
                            }
                        )
                    }

                    var showUsernameDialog by remember { mutableStateOf(false) }
                    SettingItem(
                        title = stringResource(R.string.piped_username),
                        text = pipedUsername,
                        icon = R.drawable.person,
                        onClick = { showUsernameDialog = true }
                    )
                    
                    var showPasswordDialog by remember { mutableStateOf(false) }
                    SettingItem(
                        title = stringResource(R.string.piped_password),
                        text = if (pipedPassword.isNotEmpty()) "********" else "",
                        icon = R.drawable.locked,
                        onClick = { showPasswordDialog = true },
                        modifier = Modifier.semantics { password() }
                    )

                    // Connect/Disconnect Button
                    SettingItem(
                        title = if (isPipedConnected) stringResource(R.string.piped_disconnect) 
                               else stringResource(R.string.piped_connect),
                        text = if (isPipedConnected) stringResource(R.string.piped_connected_to_s).format(pipedInstanceName) 
                               else "Not connected",
                        icon = R.drawable.piped_logo,
                        onClick = {
                            if (isPipedConnected) {
                                pipedApiToken = ""
                                pipedUsername = ""
                                pipedPassword = ""
                                android.widget.Toast.makeText(context, "Disconnected from Piped", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                if (pipedUsername.isEmpty() || pipedPassword.isEmpty()) {
                                    android.widget.Toast.makeText(context, "Please enter username and password", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    executeLogin = true
                                }
                            }
                        }
                    )
                }
            }

            // Load instances effect
            if (loadInstances) {
                LaunchedEffect(Unit) {
                    isLoading = true
                    Piped.getInstances()?.getOrNull()?.let {
                        instances = it
                    } ?: run { noInstances = true }
                    isLoading = false
                    showInstances = true
                    loadInstances = false
                }
            }

            // Show no instances message
            if (noInstances) {
                LaunchedEffect(Unit) {
                    android.widget.Toast.makeText(context, "No instances found", android.widget.Toast.LENGTH_SHORT).show()
                    noInstances = false
                }
            }

            // Execute login effect
            if (executeLogin) {
                LaunchedEffect(Unit) {
                    isLoading = true
                    val loginResult = Piped.login(
                        apiBaseUrl = Url(pipedApiBaseUrl),
                        username = pipedUsername,
                        password = pipedPassword
                    )
                    
                    loginResult?.onSuccess { sessionData ->
                        android.widget.Toast.makeText(context, "Piped login successful", android.widget.Toast.LENGTH_SHORT).show()
                        Timber.i("Piped login successful")
                        
                        pipedApiToken = sessionData.token
                        pipedApiBaseUrl = sessionData.apiBaseUrl.toString()
                    }?.onFailure {
                        Timber.e("Failed piped login ${it.stackTraceToString()}")
                        android.widget.Toast.makeText(context, "Piped login failed", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    
                    isLoading = false
                    executeLogin = false
                }
            }

            // Show instances menu
            if (showInstances && instances.isNotEmpty()) {
                menuState.display {
                    Menu {
                        instances.forEach { instance ->
                            MenuEntry(
                                icon = R.drawable.server,
                                text = instance.name,
                                secondaryText = "${instance.locationsFormatted} • Users: ${instance.userCount}",
                                onClick = {
                                    pipedApiBaseUrl = instance.apiBaseUrl.toString()
                                    pipedInstanceName = instance.name
                                    menuState.hide()
                                    showInstances = false
                                }
                            )
                        }
                        MenuEntry(
                            icon = R.drawable.chevron_back,
                            text = stringResource(R.string.cancel),
                            onClick = {
                                menuState.hide()
                                showInstances = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PipedAccountInfoDisplay(
    instanceName: String,
    username: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(colorPalette().background2, thumbnailShape())
            .padding(16.dp)
    ) {
        Text(
            text = "Piped Account",
            color = colorPalette().text,
            fontWeight = FontWeight.Bold,
            style = typography().m,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.server),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colorPalette().textSecondary
            )
            Text(
                text = "Instance: $instanceName",
                color = colorPalette().textSecondary,
                style = typography().s,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.person),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colorPalette().textSecondary
            )
            Text(
                text = "Username: $username",
                color = colorPalette().textSecondary,
                style = typography().s,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun DiscordAccountSection() {
    val context = LocalContext.current
    
    var isDiscordPresenceEnabled by rememberPreference(isDiscordPresenceEnabledKey, false)
    var loginDiscord by remember { mutableStateOf(false) }
    var discordPersonalAccessToken by rememberEncryptedPreference(
        key = discordPersonalAccessTokenKey,
        defaultValue = ""
    )
    var discordAvatar by rememberEncryptedPreference(
        key = "discord_avatar",
        defaultValue = ""
    )
    var discordUsername by rememberEncryptedPreference(
        key = "discord_username",
        defaultValue = ""
    )
    var isTokenValid by remember { mutableStateOf(true) }
    var showTokenError by remember { mutableStateOf(false) }
    
    // Check if Discord is connected
    val isDiscordConnected = remember(discordPersonalAccessToken) {
        discordPersonalAccessToken.isNotEmpty() && isTokenValid
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(1000)) + scaleIn(
            animationSpec = tween(1000),
            initialScale = 0.9f
        )
    ) {
        SectionCard(
            title = stringResource(R.string.social_discord) + " " + stringResource(R.string.beta_title),
            icon = R.drawable.logo_discord
        ) {
            SettingItem(
                title = stringResource(R.string.discord_enable_rich_presence),
                text = stringResource(R.string.beta_text),
                isChecked = isDiscordPresenceEnabled,
                onCheckedChange = { 
                    isDiscordPresenceEnabled = it
                    if (!it) {
                        RestartAppDialog.showDialog()
                    }
                },
                icon = R.drawable.musical_notes
            )

            AnimatedVisibility(visible = isDiscordPresenceEnabled) {
                Column {
                    // Account Info Display
                    if (isDiscordConnected) {
                        DiscordAccountInfoDisplay(
                            discordAvatar = discordAvatar,
                            discordUsername = discordUsername
                        )
                    }
                    
                    // Connect/Disconnect Button
                    SettingItem(
                        title = if (isDiscordConnected) stringResource(R.string.discord_disconnect) 
                               else stringResource(R.string.discord_connect),
                        text = if (isDiscordConnected) stringResource(R.string.discord_connected_to_discord_account) 
                               else "Not connected",
                        icon = if (isDiscordConnected) R.drawable.logout else R.drawable.logo_discord,
                        onClick = {
                            if (isDiscordConnected) {
                                discordPersonalAccessToken = ""
                                discordUsername = ""
                                discordAvatar = ""
                                showTokenError = false
                                android.widget.Toast.makeText(context, "Disconnected from Discord", android.widget.Toast.LENGTH_SHORT).show()
                                RestartAppDialog.showDialog()
                            } else {
                                loginDiscord = true
                            }
                        }
                    )

                    // Login Modal
                    if (loginDiscord) {
                        LoginDiscordModal(
                            loginDiscord = loginDiscord,
                            thumbnailRoundness = ThumbnailRoundness.Heavy,
                            onDismiss = { loginDiscord = false },
                            onLoginSuccess = { token, username, avatar ->
                                discordPersonalAccessToken = token
                                discordUsername = username
                                discordAvatar = avatar
                                android.widget.Toast.makeText(context, "Connected to Discord", android.widget.Toast.LENGTH_SHORT).show()
                                RestartAppDialog.showDialog()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscordAccountInfoDisplay(
    discordAvatar: String,
    discordUsername: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(colorPalette().background2, thumbnailShape())
            .padding(16.dp)
    ) {
        Text(
            text = "Discord Account",
            color = colorPalette().text,
            fontWeight = FontWeight.Bold,
            style = typography().m,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (discordAvatar.isNotEmpty()) {
                ImageCacheFactory.AsyncImage(
                    thumbnailUrl = discordAvatar,
                    contentDescription = null,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(thumbnailShape())
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.person),
                    contentDescription = null,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(thumbnailShape()),
                    tint = colorPalette().textSecondary
                )
            }
            
            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = discordUsername,
                    color = colorPalette().text,
                    fontWeight = FontWeight.Medium,
                    style = typography().m
                )
                
                Text(
                    text = "Discord Rich Presence",
                    color = colorPalette().textSecondary,
                    style = typography().s,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun LoginDiscordModal(
    loginDiscord: Boolean,
    thumbnailRoundness: ThumbnailRoundness,
    onDismiss: () -> Unit,
    onLoginSuccess: (String, String, String) -> Unit
) {
    CustomModalBottomSheet(
        showSheet = loginDiscord,
        onDismissRequest = onDismiss,
        containerColor = colorPalette().background0,
        contentColor = colorPalette().background0,
        modifier = Modifier.fillMaxWidth(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 0.dp),
                color = colorPalette().background0,
                shape = thumbnailShape()
            ) {}
        },
        shape = thumbnailRoundness.shape
    ) {
        DiscordLoginAndGetToken(
            navController = rememberNavController(),
            onGetToken = onLoginSuccess
        )
    }
}

// Enhanced YouTube login and sync functions
fun isYouTubeLoginEnabled(): Boolean {
    val isYouTubeLoginEnabled = appContext().preferences.getBoolean(enableYouTubeLoginKey, false)
    return isYouTubeLoginEnabled
}

fun isYouTubeSyncEnabled(): Boolean {
    val isYouTubeSyncEnabled = appContext().preferences.getBoolean(enableYouTubeSyncKey, false)
    val isLoggedIn = isYouTubeLoggedIn()
    val isLoginEnabled = isYouTubeLoginEnabled()
    val hasAccountInfo = hasYouTubeAccountInfo()
    Timber.d("Sync check: syncEnabled=$isYouTubeSyncEnabled, loggedIn=$isLoggedIn, loginEnabled=$isLoginEnabled, hasAccountInfo=$hasAccountInfo")
    return isYouTubeSyncEnabled && isLoggedIn && isLoginEnabled && hasAccountInfo
}

fun isYouTubeLoggedIn(): Boolean {
    val cookie = appContext().preferences.getString(ytCookieKey, "")
    val isLoggedIn = cookie?.let { parseCookieString(it) }?.contains("SAPISID") == true
    Timber.d("Login check: hasSAPISID=${cookie?.contains("SAPISID")}, isLoggedIn=$isLoggedIn")
    return isLoggedIn
}

fun hasYouTubeAccountInfo(): Boolean {
    val accountName = appContext().preferences.getString(ytAccountNameKey, "")
    val hasInfo = !accountName.isNullOrEmpty()
    Timber.d("Account info check: accountName='$accountName', hasInfo=$hasInfo")
    return hasInfo
}

fun getYouTubeSession(): it.fast4x.rimusic.extensions.youtubelogin.YoutubeSession? {
    val cookie = appContext().preferences.getString(ytCookieKey, "")
    val visitorData = appContext().preferences.getString(ytVisitorDataKey, Innertube.DEFAULT_VISITOR_DATA)
    val dataSyncId = appContext().preferences.getString(ytDataSyncIdKey, "")
    val accountName = appContext().preferences.getString(ytAccountNameKey, "")
    val accountEmail = appContext().preferences.getString(ytAccountEmailKey, "")
    val accountChannelHandle = appContext().preferences.getString(ytAccountChannelHandleKey, "")
    val accountThumbnail = appContext().preferences.getString(ytAccountThumbnailKey, "")
    
    return if (cookie != null && cookie.contains("SAPISID")) {
        YoutubeSessionManager.createSessionFromPreferences(
            cookie = cookie,
            visitorData = visitorData ?: Innertube.DEFAULT_VISITOR_DATA,
            dataSyncId = dataSyncId ?: "",
            accountName = accountName ?: "",
            accountEmail = accountEmail ?: "",
            accountChannelHandle = accountChannelHandle ?: "",
            accountThumbnail = accountThumbnail ?: ""
        )
    } else {
        null
    }
}

fun getLatestYouTubeCookie(): String? {
    val cookie = appContext().preferences.getString(ytCookieKey, "")
    Timber.d("Getting latest cookie: hasSAPISID=${cookie?.contains("SAPISID")}")
    return if (cookie?.contains("SAPISID") == true) cookie else null
}

/// Enhanced function to refresh account info from API with fallback
suspend fun refreshYouTubeAccountInfo(): Boolean {
    return try {
        Timber.d("Refreshing YouTube account info...")
        
        // Get cookie from preferences
        val cookie = appContext().preferences.getString(ytCookieKey, "")
        if (cookie.isNullOrEmpty() || !cookie.contains("SAPISID")) {
            Timber.w("No valid cookie found for account info refresh")
            return false
        }
        
        val accountInfo = Innertube.accountInfo().getOrNull()
        
        if (accountInfo != null) {
            val prefs = appContext().preferences.edit()
            accountInfo.name?.let { prefs.putString(ytAccountNameKey, it) }
            accountInfo.email?.let { prefs.putString(ytAccountEmailKey, it) }
            accountInfo.channelHandle?.let { prefs.putString(ytAccountChannelHandleKey, it) }
            accountInfo.thumbnailUrl?.let { prefs.putString(ytAccountThumbnailKey, it) }
            prefs.apply()
            
            Timber.d("Successfully refreshed account info: ${accountInfo.name}")
            true
        } else {
            Timber.w("No account info returned from API")
            
            // Fallback: Try to extract from existing session
            val session = getYouTubeSession()
            if (session != null && session.accountName.isNotEmpty()) {
                Timber.d("Using session data as fallback: ${session.accountName}")
                true
            } else {
                false
            }
        }
    } catch (e: Exception) {
        Timber.e("Error refreshing account info: ${e.message}")
        false
    }
}