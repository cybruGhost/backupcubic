package it.fast4x.rimusic.ui.screens.cubicjam

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import it.fast4x.rimusic.LocalPlayerAwareWindowInsets
import it.fast4x.rimusic.ui.components.themed.Title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import it.fast4x.rimusic.context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Get Cubic Jam user info from token
 */
suspend fun fetchCubicJamUser(token: String): Triple<String, String, String>? = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val request = Request.Builder()
        .url("https://dkbvirgavjojuyaazzun.supabase.co/auth/v1/user")
        .header("Authorization", "Bearer $token")
        .get()
        .build()
    runCatching {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Timber.tag("CubicJam").e("Failed to fetch user: ${response.code}")
                return@withContext null
            }
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val id = json.getString("id")
            val email = json.getString("email")
            val userMetadata = json.optJSONObject("user_metadata")
            val username = userMetadata?.optString("username") ?: email.split("@")[0]
            Triple(id, username, email)
        }
    }.getOrElse { exception ->
        Timber.tag("CubicJam").e(exception, "Error fetching user: ${exception.message}")
        null
    }
}

/**
 * JavaScript to extract Cubic Jam token from localStorage
 */
private const val JS_SNIPPET = """
javascript:(function(){
    try {
        // Get all localStorage keys
        const keys = Object.keys(localStorage);
        let token = null;
        let userId = null;
        let username = null;
        let email = null;
        
        for (const key of keys) {
            if (key.includes('dkbvirgavjojuyaazzun') || key.includes('auth-token') || key.includes('supabase.auth.token')) {
                const data = JSON.parse(localStorage.getItem(key));
                if (data && data.access_token) {
                    token = data.access_token;
                    if (data.user) {
                        userId = data.user.id;
                        email = data.user.email;
                        username = data.user.user_metadata?.username || email.split('@')[0];
                    }
                    break;
                }
            }
        }
        
        if (token && userId && email) {
            alert(JSON.stringify({
                token: token,
                userId: userId,
                username: username,
                email: email
            }));
            return;
        }
        
        alert(JSON.stringify({error: 'No Cubic Jam token found in localStorage'}));
    } catch(e) {
        alert(JSON.stringify({error: e.message}));
    }
})()
"""

private const val MOTOROLA = "motorola"
private const val SAMSUNG_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; SM-S921U; Build/UP1A.231005.007) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.363"

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CubicJamLogin(
    navController: NavController,
    onGetToken: (String, String, String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var webView: WebView? = null

    Column(
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
    ) {
        // Option 1: If Title has title parameter
        Title(
            title = "Login to Cubic Jam",
            onClick = { navController.navigateUp() }
        )

        // OR Option 2: If Title doesn't exist in your app, use a simple Text
        // Row(
        //     modifier = Modifier.fillMaxWidth(),
        //     horizontalArrangement = Arrangement.Start
        // ) {
        //     IconButton(onClick = { navController.navigateUp() }) {
        //         Icon(
        //             painter = painterResource(R.drawable.chevron_down),
        //             contentDescription = "Back"
        //         )
        //     }
        //     Text(
        //         text = "Login to Cubic Jam",
        //         style = MaterialTheme.typography.headlineSmall,
        //         modifier = Modifier.padding(16.dp)
        //     )
        // }

        AndroidView(
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webViewClient = object : WebViewClient() {
                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(webView: WebView, url: String): Boolean {
                            webView.stopLoading()
                            if (url.contains("jam-wave-connect.lovable.app/feed")) {
                                webView.loadUrl(JS_SNIPPET)
                                webView.visibility = View.GONE
                            }
                            return false
                        }
                        override fun onPageFinished(view: WebView, url: String) {
                            if (url.contains("jam-wave-connect.lovable.app/feed")) {
                                view.loadUrl(JS_SNIPPET)
                            }
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onJsAlert(
                            view: WebView,
                            url: String,
                            message: String,
                            result: JsResult,
                        ): Boolean {
                            scope.launch(Dispatchers.Main) {
                                try {
                                    val json = JSONObject(message)
                                    if (json.has("error")) {
                                        Timber.tag("CubicJam").e("Error: ${json.getString("error")}")
                                        result.confirm()
                                        return@launch
                                    }
                                    
                                    val token = json.getString("token")
                                    val userId = json.getString("userId")
                                    val username = json.getString("username")
                                    val email = json.getString("email")
                                    
                                    onGetToken(token, userId, username, email)
                                    navController.navigateUp()
                                } catch (e: Exception) {
                                    // If parsing fails, try to fetch user info
                                    val token = message
                                    if (token.startsWith("eyJ")) { // Looks like a JWT token
                                        val user = fetchCubicJamUser(token)
                                        if (user != null) {
                                            onGetToken(token, user.first, user.second, user.third)
                                        } else {
                                            onGetToken(token, "unknown", "user", "unknown@example.com")
                                        }
                                        navController.navigateUp()
                                    }
                                }
                            }
                            this@apply.visibility = View.GONE
                            result.confirm()
                            return true
                        }
                    }
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    if (android.os.Build.MANUFACTURER.equals(MOTOROLA, ignoreCase = true)) {
                        settings.userAgentString = SAMSUNG_USER_AGENT
                    }
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.removeAllCookies(null)
                    cookieManager.flush()
                    WebStorage.getInstance().deleteAllData()
                    webView = this
                    
                    // Load Cubic Jam website
                    loadUrl("https://jam-wave-connect.lovable.app/auth")
                }
            }
        )

        BackHandler(enabled = webView?.canGoBack() == true) {
            webView?.goBack()
        }
    }
}