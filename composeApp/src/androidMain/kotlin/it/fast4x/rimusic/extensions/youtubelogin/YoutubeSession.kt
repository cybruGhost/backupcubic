package it.fast4x.rimusic.extensions.youtubelogin

data class YoutubeSession(
    val cookie: String = "",
    val visitorData: String = "",
    val dataSyncId: String = "",
    val accountName: String = "",
    val accountEmail: String = "",
    val accountChannelHandle: String = "",
    val accountThumbnail: String = "",
    val isLoggedIn: Boolean = false
)

// Helper functions for YouTube session management
object YoutubeSessionManager {
    private var currentSession: YoutubeSession? = null
    
    fun updateSession(session: YoutubeSession) {
        currentSession = session
    }
    
    fun getCurrentSession(): YoutubeSession? = currentSession
    
    fun clearSession() {
        currentSession = null
    }
    
    fun createSessionFromPreferences(
        cookie: String,
        visitorData: String = "",
        dataSyncId: String = "",
        accountName: String = "",
        accountEmail: String = "",
        accountChannelHandle: String = "",
        accountThumbnail: String = ""
    ): YoutubeSession {
        return YoutubeSession(
            cookie = cookie,
            visitorData = visitorData,
            dataSyncId = dataSyncId,
            accountName = accountName,
            accountEmail = accountEmail,
            accountChannelHandle = accountChannelHandle,
            accountThumbnail = accountThumbnail,
            isLoggedIn = cookie.contains("SAPISID")
        )
    }
    
    // Helper to extract basic info from cookie
    fun extractInfoFromCookie(cookie: String): Pair<String, String> {
        val emailMatch = Regex("email=([^;]+)").find(cookie)
        val nameMatch = Regex("name=([^;]+)").find(cookie)
        
        val extractedName = nameMatch?.groupValues?.get(1) ?: ""
        val extractedEmail = emailMatch?.groupValues?.get(1) ?: ""
        
        return Pair(extractedName, extractedEmail)
    }
}