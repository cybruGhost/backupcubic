package it.fast4x.rimusic.extensions.youtubelogin

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import it.fast4x.innertube.Innertube
import it.fast4x.rimusic.LocalPlayerAwareWindowInsets
import app.kreate.android.R
import it.fast4x.rimusic.ui.components.themed.Title
import it.fast4x.rimusic.utils.ytVisitorDataKey
import it.fast4x.rimusic.utils.ytCookieKey
import it.fast4x.rimusic.utils.ytAccountNameKey
import it.fast4x.rimusic.utils.ytAccountEmailKey
import it.fast4x.rimusic.utils.ytAccountChannelHandleKey
import it.fast4x.rimusic.utils.rememberPreference
import it.fast4x.rimusic.utils.ytAccountThumbnailKey
import it.fast4x.rimusic.utils.ytDataSyncIdKey
import kotlinx.coroutines.launch
import timber.log.Timber

// Create a separate class for JavaScript interface
class YouTubeLoginJSInterface(
    private val onVisitorData: (String) -> Unit,
    private val onDataSyncId: (String) -> Unit
) {
    @JavascriptInterface
    fun onRetrieveVisitorData(newVisitorData: String?) {
        newVisitorData?.let {
            Timber.d("YouTubeLogin: Received VISITOR_DATA: $it")
            onVisitorData(it)
        }
    }
    
    @JavascriptInterface
    fun onRetrieveDataSyncId(newDataSyncId: String?) {
        newDataSyncId?.let {
            Timber.d("YouTubeLogin: Received DATASYNC_ID: $it")
            onDataSyncId(it)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLogin(
    onLogin: (String, String, String, String, String) -> Unit,
    isSwitchingAccount: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var visitorData by rememberPreference(key = ytVisitorDataKey, defaultValue = Innertube.DEFAULT_VISITOR_DATA)
    var dataSyncId by rememberPreference(key = ytDataSyncIdKey, defaultValue = "")
    var cookie by rememberPreference(key = ytCookieKey, defaultValue = "")
    var accountName by rememberPreference(key = ytAccountNameKey, defaultValue = "")
    var accountEmail by rememberPreference(key = ytAccountEmailKey, defaultValue = "")
    var accountChannelHandle by rememberPreference(key = ytAccountChannelHandleKey, defaultValue = "")
    var accountThumbnail by rememberPreference(key = ytAccountThumbnailKey, defaultValue = "")

    var webView: WebView? = null

    // When switching accounts, clear ALL existing data
    LaunchedEffect(isSwitchingAccount) {
        if (isSwitchingAccount) {
            Timber.d("YouTubeLogin: Switching account - clearing all data")
            
            // Clear web storage
            WebStorage.getInstance().deleteAllData()
            
            // Clear cookies
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
            
            // Clear session
            YoutubeSessionManager.clearSession()
        }
    }

    Column(
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
    ) {
        Title(
            title = if (isSwitchingAccount) "Switch YouTube Account" else "Login to YouTube Music",
            icon = R.drawable.chevron_down,
            onClick = {
                // Just pass the cookie - let the main screen handle account info
                onLogin(cookie, accountName, accountEmail, accountChannelHandle, accountThumbnail)
            }
        )

        AndroidView(
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = object : WebViewClient() {
                        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                            Timber.d("YouTubeLogin: URL changed to: $url")
                            
                            if (url.startsWith("https://music.youtube.com")) {
                                val freshCookie = CookieManager.getInstance().getCookie(url)
                                if (freshCookie != null && freshCookie.isNotEmpty()) {
                                    cookie = freshCookie
                                    Timber.d("YouTubeLogin: Updated cookie (has SAPISID: ${freshCookie.contains("SAPISID")})")
                                    
                                    // Check if we have SAPISID (means logged in)
                                    if (freshCookie.contains("SAPISID")) {
                                        Timber.d("YouTubeLogin: User is logged in!")
                                        
                                        // Immediately return success with just the cookie
                                        // Account info will be fetched later
                                        onLogin(cookie, "", "", "", "")
                                    }
                                }
                            }
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            super.onPageFinished(view, url)
                            Timber.d("YouTubeLogin: Page finished: $url")
                            
                            // Try to get visitor data and data sync id via JavaScript
                            loadUrl("javascript:Android.onRetrieveVisitorData(window.yt.config_.VISITOR_DATA)")
                            loadUrl("javascript:Android.onRetrieveDataSyncId(window.yt.config_.DATASYNC_ID)")
                        }
                    }
                    
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        loadsImagesAutomatically = true
                        
                        // Fix user agent (important!)
                        val userAgent = settings.userAgentString
                        settings.userAgentString = userAgent.replace("; wv", "")
                    }
                    
                    // Enable cookies
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                    
                    // Add JavaScript interface
                    addJavascriptInterface(YouTubeLoginJSInterface(
                        onVisitorData = { newVisitorData ->
                            Timber.d("YouTubeLogin: Received visitorData: $newVisitorData")
                            visitorData = newVisitorData
                        },
                        onDataSyncId = { newDataSyncId ->
                            Timber.d("YouTubeLogin: Received dataSyncId: $newDataSyncId")
                            dataSyncId = newDataSyncId
                        }
                    ), "Android")
                    
                    webView = this

                    // Load the appropriate URL
                    val url = if (!isSwitchingAccount && cookie.isNotEmpty() && cookie.contains("SAPISID")) {
                        // If NOT switching and have valid cookie, go to music.youtube.com
                        Timber.d("YouTubeLogin: Already have cookie, going to music.youtube.com")
                        "https://music.youtube.com"
                    } else {
                        // If switching account OR no valid cookie, force login page
                        Timber.d("YouTubeLogin: Showing login page (switching=$isSwitchingAccount)")
                        // Always clear cookies when showing login page
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.removeAllCookies(null)
                        cookieManager.flush()
                        WebStorage.getInstance().deleteAllData()
                        "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
                    }
                    
                    loadUrl(url)
                }
            }
        )

        BackHandler(enabled = webView?.canGoBack() == true) {
            webView?.goBack()
        }
    }
}