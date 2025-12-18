package it.fast4x.rimusic.ui.screens.rewind

import it.fast4x.rimusic.Database
import it.fast4x.rimusic.models.Song
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

// Data classes for rewind stats
data class TopSong(val title: String, val artist: String, val playCount: Int)
data class TopArtist(val name: String, val playCount: Int)
data class ListeningStats(
    val totalPlays: Int,
    val listeningHours: Double,
    val mostActiveDay: String?,
    val mostActiveHour: String?,
    val firstPlayDate: String?,
    val lastPlayDate: String?
    // totalUniqueSongs is NOT here - it's in RewindData
)
data class RewindData(
    val topSongs: List<TopSong>,
    val topArtists: List<TopArtist>,
    val stats: ListeningStats,
    val favoriteGenre: String?,
    val totalUniqueSongs: Int  // This is here
)

// Data fetcher class
object RewindDataFetcher {
    
    // Get data for current year
    suspend fun getRewindData(): RewindData {
        return try {
            // Get current year boundaries
            val currentYear = LocalDateTime.now().year
            val yearStart = LocalDateTime.of(currentYear, 1, 1, 0, 0, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val yearEnd = LocalDateTime.now()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            
            // Get all events with songs from database
            val allEventsWithSongs = Database.eventTable.allWithSong().first()
            
            if (allEventsWithSongs.isEmpty()) {
                return createEmptyData()
            }
            
            // Filter events for current year
            val yearlyEvents = allEventsWithSongs.filter { 
                it.event.timestamp in yearStart..yearEnd 
            }
            
            // If no data for current year, return empty stats
            if (yearlyEvents.isEmpty()) {
                return createEmptyData()
            }
            
            // Extract events and songs
            val events = yearlyEvents.map { it.event }
            val songs = yearlyEvents.mapNotNull { it.song }.distinctBy { it.id }
            
            // Calculate top songs (with actual play counts from events)
            val topSongs = calculateTopSongs(events, songs)
            
            // Calculate top artists
            val topArtists = calculateTopArtists(events, songs)
            
            // Calculate listening stats
            val stats = calculateListeningStats(events)
            
            // Calculate total unique songs
            val totalUniqueSongs = songs.size
            
            // Get favorite genre (simplified - you'll need to implement this based on your data)
            val favoriteGenre = getMostFrequentArtistGenre(events, songs)
            
            RewindData(
                topSongs = topSongs.take(10),
                topArtists = topArtists.take(10),
                stats = stats,
                favoriteGenre = favoriteGenre,
                totalUniqueSongs = totalUniqueSongs
            )
            
        } catch (e: Exception) {
            e.printStackTrace()
            createEmptyData()
        }
    }
    
    private fun calculateTopSongs(events: List<it.fast4x.rimusic.models.Event>, songs: List<Song>): List<TopSong> {
        // Group events by song ID and count plays
        val songPlayCounts = events
            .groupingBy { it.songId }
            .eachCount()
            .toList()
            .sortedByDescending { (_, count) -> count }
        
        return songPlayCounts.mapNotNull { (songId, playCount) ->
            val song = songs.find { it.id == songId }
            song?.let {
                TopSong(
                    title = it.title ?: "Unknown Song",
                    artist = it.artistsText ?: "Unknown Artist",
                    playCount = playCount
                )
            }
        }
    }
    
    private fun calculateTopArtists(events: List<it.fast4x.rimusic.models.Event>, songs: List<Song>): List<TopArtist> {
        // Create a map to track artist play counts
        val artistPlayCounts = mutableMapOf<String, Int>()
        
        events.forEach { event ->
            val song = songs.find { it.id == event.songId }
            val artistName = song?.artistsText ?: "Unknown Artist"
            
            // If multiple artists are in one string, we'll use the first one
            val mainArtist = artistName.split(",").firstOrNull()?.trim() ?: artistName
            
            artistPlayCounts[mainArtist] = artistPlayCounts.getOrDefault(mainArtist, 0) + 1
        }
        
        return artistPlayCounts
            .toList()
            .sortedByDescending { (_, count) -> count }
            .map { (artistName, playCount) ->
                TopArtist(artistName, playCount)
            }
    }
    
    private fun calculateListeningStats(events: List<it.fast4x.rimusic.models.Event>): ListeningStats {
        val totalPlays = events.size
        
        // Calculate total listening time in milliseconds
        val totalPlayTimeMs = events.sumOf { it.playTime }
        val listeningHours = totalPlayTimeMs / (1000.0 * 60.0 * 60.0)
        
        val timestamps = events.map { it.timestamp }
        
        if (timestamps.isEmpty()) {
            return ListeningStats(
                totalPlays = totalPlays,
                listeningHours = listeningHours,
                mostActiveDay = null,
                mostActiveHour = null,
                firstPlayDate = null,
                lastPlayDate = null
            )
        }
        
        // Convert timestamps to LocalDateTime for analysis
        val dateTimes = timestamps.map { timestamp ->
            Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()
        }
        
        // Most active day of week
        val dayCounts = dateTimes.groupBy { it.dayOfWeek }
        val mostActiveDay = dayCounts.maxByOrNull { it.value.size }?.key?.let {
            it.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
        }
        
        // Most active hour
        val hourCounts = dateTimes.groupBy { it.hour }
        val mostActiveHour = hourCounts.maxByOrNull { it.value.size }?.key?.let {
            String.format("%02d:00", it)
        }
        
        // First and last play dates
        val firstPlayDate = timestamps.minOrNull()?.let { formatDate(it) }
        val lastPlayDate = timestamps.maxOrNull()?.let { formatDate(it) }
        
        return ListeningStats(
            totalPlays = totalPlays,
            listeningHours = listeningHours,
            mostActiveDay = mostActiveDay,
            mostActiveHour = mostActiveHour,
            firstPlayDate = firstPlayDate,
            lastPlayDate = lastPlayDate
        )
    }
    
    private fun getMostFrequentArtistGenre(events: List<it.fast4x.rimusic.models.Event>, songs: List<Song>): String? {
        // This is a simplified version. Since we don't have genre data in the Song model,
        // we'll analyze artist names to guess genres
        
        val artistFrequency = mutableMapOf<String, Int>()
        
        events.forEach { event ->
            val song = songs.find { it.id == event.songId }
            val artistName = song?.artistsText ?: "Unknown"
            
            // Clean up artist name (take first artist if multiple)
            val mainArtist = artistName.split(",").firstOrNull()?.trim() ?: artistName
            
            artistFrequency[mainArtist] = artistFrequency.getOrDefault(mainArtist, 0) + 1
        }
        
        // Find the most frequent artist
        val mostFrequentArtist = artistFrequency.maxByOrNull { it.value }?.key
        
        // Simple genre detection based on artist name patterns
        return mostFrequentArtist?.let { detectGenreFromArtist(it) }
    }
    
    private fun detectGenreFromArtist(artistName: String): String {
        // Very basic genre detection - you should implement proper genre detection
        // based on your actual music library or external API
        
        val artistLower = artistName.lowercase(Locale.getDefault())
        
        return when {
            artistLower.contains("rock") || artistLower.contains("metal") -> "Rock"
            artistLower.contains("pop") -> "Pop"
            artistLower.contains("rap") || artistLower.contains("hip") -> "Hip-Hop"
            artistLower.contains("jazz") || artistLower.contains("blues") -> "Jazz/Blues"
            artistLower.contains("classical") || artistLower.contains("orchestra") -> "Classical"
            artistLower.contains("electronic") || artistLower.contains("edm") || artistLower.contains("techno") -> "Electronic"
            artistLower.contains("country") -> "Country"
            artistLower.contains("reggae") -> "Reggae"
            artistLower.contains("rnb") || artistLower.contains("r&b") -> "R&B"
            else -> "Various"
        }
    }
    
    private fun formatDate(timestamp: Long): String {
        return try {
            Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault()))
        } catch (e: Exception) {
            "Unknown Date"
        }
    }
    
    private fun createEmptyData(): RewindData {
        return RewindData(
            topSongs = emptyList(),
            topArtists = emptyList(),
            stats = ListeningStats(
                totalPlays = 0,
                listeningHours = 0.0,
                mostActiveDay = null,
                mostActiveHour = null,
                firstPlayDate = null,
                lastPlayDate = null
            ),
            favoriteGenre = null,
            totalUniqueSongs = 0
        )
    }
}