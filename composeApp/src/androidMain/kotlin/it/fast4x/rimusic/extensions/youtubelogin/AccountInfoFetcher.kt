package it.fast4x.rimusic.extensions.youtubelogin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.AccountInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class AccountInfoFetcher {
    
    companion object {
        suspend fun fetchAccountInfo(cookie: String? = null): AccountInfo? {
            return withContext(Dispatchers.IO) {
                try {
                    Timber.d("AccountInfoFetcher: Starting account info fetch with cookie length: ${cookie?.length ?: 0}")
                    
                    if (cookie.isNullOrEmpty()) {
                        Timber.e("AccountInfoFetcher: No cookie provided")
                        return@withContext null
                    }
                    
                    // Try to get account info using InnerTube directly
                    return@withContext tryGetAccountInfoViaInnerTube(cookie)
                    
                } catch (e: Exception) {
                    Timber.e("AccountInfoFetcher: Error fetching account info: ${e.message}")
                    null
                }
            }
        }
        
        private suspend fun tryGetAccountInfoViaInnerTube(cookie: String): AccountInfo? {
            return try {
                Timber.d("AccountInfoFetcher: Trying to get account info via InnerTube")
                
                // Parse SAPISID from cookie
                val cookies = parseCookieString(cookie)
                val sapisid = cookies["SAPISID"]
                
                if (sapisid == null) {
                    Timber.e("AccountInfoFetcher: No SAPISID found in cookie")
                    return null
                }
                
                Timber.d("AccountInfoFetcher: Found SAPISID: ${sapisid.take(10)}...")
                
                // Try to use InnerTube to get account info
                // This assumes InnerTube has methods to fetch account info
                // You might need to adjust based on your actual InnerTube implementation
                val accountInfo = try {
                    // Try to get account info from music.youtube.com homepage
                    getAccountInfoFromYTMHomepage(cookie)
                } catch (e: Exception) {
                    Timber.e("AccountInfoFetcher: Failed to get account info via homepage: ${e.message}")
                    null
                }
                
                accountInfo ?: try {
                    // Fallback: Create basic account info from cookie
                    createAccountInfoFromCookie(cookie)
                } catch (e: Exception) {
                    Timber.e("AccountInfoFetcher: Failed to create account info from cookie: ${e.message}")
                    null
                }
                
            } catch (e: Exception) {
                Timber.e("AccountInfoFetcher: InnerTube method failed: ${e.message}")
                null
            }
        }
        
        private suspend fun getAccountInfoFromYTMHomepage(cookie: String): AccountInfo? {
            return try {
                val client = HttpClient()
                
                val response: HttpResponse = client.get("https://music.youtube.com") {
                    header("Cookie", cookie)
                    header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                    header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    header("Accept-Language", "en-US,en;q=0.9")
                }
                
                val html = response.body<String>()
                
                // Parse account name from HTML response
                val accountName = parseAccountNameFromHTML(html)
                
                if (accountName != null) {
                    AccountInfo(
                        name = accountName,
                        email = "", // Can't get email from HTML
                        channelHandle = parseChannelHandleFromCookie(cookie),
                        thumbnailUrl = parseThumbnailUrlFromHTML(html)
                    )
                } else {
                    null
                }
                
            } catch (e: Exception) {
                Timber.e("AccountInfoFetcher: Error getting YTM homepage: ${e.message}")
                null
            }
        }
        
        private fun createAccountInfoFromCookie(cookie: String): AccountInfo? {
            // Extract basic info from cookie
            val cookies = parseCookieString(cookie)
            
            // Try to get account identifier from SAPISID
            val sapisid = cookies["SAPISID"] ?: return null
            
            // SAPISID format: SAPISID=xxx/account_name@timestamp
            val sapisidParts = sapisid.split("/")
            val accountIdentifier = if (sapisidParts.size > 1) {
                sapisidParts[1].split("@").firstOrNull() ?: "YouTube User"
            } else {
                "YouTube User"
            }
            
            // Clean up the identifier
            val cleanName = accountIdentifier
                .replace("%20", " ")
                .replace("%40", "@")
                .replace("_", " ")
                .trim()
            
            return AccountInfo(
                name = if (cleanName.isNotEmpty()) cleanName else "YouTube Account",
                email = "", // Can't get email from cookie
                channelHandle = "", // Can't get channel handle from cookie
                thumbnailUrl = "" // Can't get thumbnail from cookie
            )
        }
        
        private fun parseCookieString(cookie: String): Map<String, String> {
            return cookie.split(";")
                .map { it.trim() }
                .filter { it.contains("=") }
                .associate {
                    val parts = it.split("=", limit = 2)
                    parts[0] to (parts.getOrNull(1) ?: "")
                }
        }
        
        private fun parseAccountNameFromHTML(html: String): String? {
            // Try to find account name in YouTube Music HTML
            val patterns = listOf(
                Regex("\"accountName\":\"([^\"]+)\""),
                Regex("\"displayName\":\"([^\"]+)\""),
                Regex("ytmAccountName[^>]*>([^<]+)<"),
                Regex("account-name[^>]*>([^<]+)<"),
                Regex("ytmusic-user-email-renderer[^>]*title=\"([^\"]+)\""),
                Regex("\"authorName\":\"([^\"]+)\"")
            )
            
            for (pattern in patterns) {
                val match = pattern.find(html)
                match?.let {
                    val name = it.groupValues.getOrNull(1)?.trim()
                    if (!name.isNullOrEmpty() && name.length < 100 && name != "null") {
                        Timber.d("AccountInfoFetcher: Found account name: $name")
                        return name
                    }
                }
            }
            
            // Try to find in ytInitialData
            val ytInitialDataMatch = Regex("ytInitialData\\s*=\\s*({[^;]+});").find(html)
            ytInitialDataMatch?.let {
                try {
                    val jsonStr = it.groupValues.getOrNull(1)
                    if (jsonStr != null) {
                        // Simple extraction from JSON
                        val nameMatch = Regex("\"displayName\":\"([^\"]+)\"").find(jsonStr)
                        nameMatch?.let { match ->
                            val name = match.groupValues.getOrNull(1)?.trim()
                            if (!name.isNullOrEmpty() && name != "null") {
                                Timber.d("AccountInfoFetcher: Found displayName in ytInitialData: $name")
                                return name
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e("AccountInfoFetcher: Error parsing ytInitialData: ${e.message}")
                }
            }
            
            return null
        }
        
        private fun parseChannelHandleFromCookie(cookie: String): String {
            // Try to extract from cookie if possible
            // This is a placeholder - actual implementation depends on cookie structure
            return ""
        }
        
        private fun parseThumbnailUrlFromHTML(html: String): String {
            // Try to find thumbnail URL in HTML
            val patterns = listOf(
                Regex("\"thumbnailUrl\":\"([^\"]+)\""),
                Regex("avatar-img[^>]*src=\"([^\"]+)\""),
                Regex("yt-img-shadow[^>]*src=\"([^\"]+)\"")
            )
            
            for (pattern in patterns) {
                val match = pattern.find(html)
                match?.let {
                    val url = it.groupValues.getOrNull(1)?.trim()
                    if (!url.isNullOrEmpty() && url.startsWith("http")) {
                        return url
                    }
                }
            }
            
            return ""
        }
    }
}